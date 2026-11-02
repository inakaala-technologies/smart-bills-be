package com.bhive.payslip.controller;

import com.bhive.business.entity.Business;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.service.S3ImageUploadService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import com.bhive.payslip.entity.BusinessEmployee;
import com.bhive.payslip.entity.Payslip;
import com.bhive.payslip.repository.BusinessEmployeeRepository;
import com.bhive.payslip.repository.PayslipRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class PayslipController {

    public record EmployeeInput(String identifier, String employeeCode, String designation) {}

    public record EmployeeView(Long id, Long businessId, Long customerProfileId, String name, String email,
                               String phone, String employeeCode, String designation, LocalDateTime createdAt) {}

    public record PayslipView(Long id, Long businessId, String businessName, Long employeeId, String employeeName,
                              int payMonth, int payYear, String periodLabel, String note, String fileName,
                              String contentType, LocalDateTime createdAt) {}

    private final BusinessRepository businessRepository;
    private final BusinessEmployeeRepository employeeRepository;
    private final PayslipRepository payslipRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessMembershipService businessMembershipService;
    private final NotificationService notificationService;
    private final S3ImageUploadService s3Service;

    public PayslipController(BusinessRepository businessRepository, BusinessEmployeeRepository employeeRepository,
                             PayslipRepository payslipRepository, CustomerProfileRepository customerProfileRepository,
                             BusinessMembershipService businessMembershipService,
                             NotificationService notificationService, S3ImageUploadService s3Service) {
        this.businessRepository = businessRepository;
        this.employeeRepository = employeeRepository;
        this.payslipRepository = payslipRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessMembershipService = businessMembershipService;
        this.notificationService = notificationService;
        this.s3Service = s3Service;
    }

    @PutMapping("/businesses/{businessId}/payslips-enabled")
    public Business setPayslipsEnabled(@PathVariable Long businessId, @RequestParam boolean enabled) {
        Business business = requireManager(businessId);
        business.setPayslipsEnabled(enabled);
        return businessRepository.save(business);
    }

    @GetMapping("/businesses/{businessId}/employees")
    public List<EmployeeView> listEmployees(@PathVariable Long businessId) {
        requireEnabledManager(businessId);
        Long tenantId = TenantContext.getTenantId();
        List<BusinessEmployee> employees = employeeRepository
            .findByTenantIdAndBusinessIdAndStatusOrderByCreatedAtDesc(tenantId, businessId, BusinessEmployee.ACTIVE);
        Map<Long, CustomerProfile> profiles = profilesById(tenantId, employees);
        return employees.stream().map(employee -> toView(employee, profiles.get(employee.getCustomerProfileId()))).toList();
    }

    @PostMapping("/businesses/{businessId}/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeView addEmployee(@PathVariable Long businessId, @RequestBody EmployeeInput input) {
        requireEnabledManager(businessId);
        Long tenantId = TenantContext.getTenantId();
        String identifier = input == null || input.identifier() == null ? "" : input.identifier().trim();
        if (identifier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the employee's email, phone or profile ID.");
        }
        CustomerProfile profile = findRegisteredCustomer(identifier, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No registered app user found with that email, phone or profile ID."));

        BusinessEmployee employee = employeeRepository
            .findByTenantIdAndBusinessIdAndCustomerProfileId(tenantId, businessId, profile.getId())
            .orElseGet(BusinessEmployee::new);
        if (employee.getId() != null && BusinessEmployee.ACTIVE.equals(employee.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This person is already an employee.");
        }
        employee.setTenantId(tenantId);
        employee.setBusinessId(businessId);
        employee.setCustomerProfileId(profile.getId());
        employee.setUserId(profile.getUserId());
        employee.setEmployeeCode(clean(input.employeeCode(), 40));
        employee.setDesignation(clean(input.designation(), 120));
        employee.setStatus(BusinessEmployee.ACTIVE);
        return toView(employeeRepository.save(employee), profile);
    }

    @DeleteMapping("/businesses/{businessId}/employees/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeEmployee(@PathVariable Long businessId, @PathVariable Long employeeId) {
        requireEnabledManager(businessId);
        BusinessEmployee employee = findEmployee(businessId, employeeId);
        employee.setStatus(BusinessEmployee.REMOVED);
        employeeRepository.save(employee);
    }

    @PostMapping(value = "/businesses/{businessId}/employees/{employeeId}/payslips",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PayslipView sendPayslip(@PathVariable Long businessId, @PathVariable Long employeeId,
                                   @RequestParam int month, @RequestParam int year,
                                   @RequestParam(required = false) String note,
                                   @RequestParam("file") MultipartFile file) {
        Business business = requireEnabledManager(businessId);
        BusinessEmployee employee = findEmployee(businessId, employeeId);
        if (!BusinessEmployee.ACTIVE.equals(employee.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This employee has been removed.");
        }
        if (month < 1 || month > 12 || year < 2000 || year > LocalDate.now().getYear() + 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid pay period.");
        }
        S3ImageUploadService.StoredDocument stored;
        try {
            stored = s3Service.uploadPayslipDocument(file, businessId, employeeId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        Long tenantId = TenantContext.getTenantId();
        Payslip payslip = new Payslip();
        payslip.setTenantId(tenantId);
        payslip.setBusinessId(businessId);
        payslip.setEmployeeId(employeeId);
        payslip.setRecipientUserId(employee.getUserId());
        payslip.setPayMonth(month);
        payslip.setPayYear(year);
        payslip.setNote(clean(note, 300));
        payslip.setFileUrl(stored.url());
        payslip.setFileName(safeFileName(file.getOriginalFilename(), stored.contentType()));
        payslip.setContentType(stored.contentType());
        payslip.setSentByUserId(TenantContext.getUserId());
        Payslip saved = payslipRepository.save(payslip);

        notificationService.create(tenantId, employee.getUserId(), "PAYSLIP_RECEIVED",
            "Payslip received from " + business.getName(),
            "Your payslip for " + period(month, year) + " is available.", "/customer/dashboard/payslips");
        return toView(saved, business.getName(), null);
    }

    @GetMapping("/businesses/{businessId}/payslips")
    public List<PayslipView> listBusinessPayslips(@PathVariable Long businessId,
                                                  @RequestParam(required = false) Long employeeId) {
        Business business = requireEnabledManager(businessId);
        Long tenantId = TenantContext.getTenantId();
        List<Payslip> payslips = employeeId == null
            ? payslipRepository.findTop200ByTenantIdAndBusinessIdOrderByCreatedAtDesc(tenantId, businessId)
            : payslipRepository.findTop200ByTenantIdAndBusinessIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, businessId, employeeId);
        List<BusinessEmployee> employees = employeeRepository.findAllById(
            payslips.stream().map(Payslip::getEmployeeId).distinct().toList());
        Map<Long, CustomerProfile> profiles = profilesById(tenantId, employees);
        Map<Long, String> names = employees.stream().collect(Collectors.toMap(BusinessEmployee::getId,
            employee -> Optional.ofNullable(profiles.get(employee.getCustomerProfileId()))
                .map(CustomerProfile::getName).orElse("Employee")));
        return payslips.stream().map(p -> toView(p, business.getName(), names.get(p.getEmployeeId()))).toList();
    }

    @GetMapping("/customers/my/payslips")
    public List<PayslipView> myPayslips() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = requireUser();
        List<Payslip> payslips = payslipRepository
            .findTop200ByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(tenantId, userId);
        Map<Long, String> businessNames = businessRepository.findAllById(
            payslips.stream().map(Payslip::getBusinessId).distinct().toList()).stream()
            .collect(Collectors.toMap(Business::getId, b -> b.getName() == null ? "Business" : b.getName()));
        return payslips.stream().map(p -> toView(p, businessNames.get(p.getBusinessId()), null)).toList();
    }

    @GetMapping("/payslips/{payslipId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long payslipId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = requireUser();
        Payslip payslip = payslipRepository.findByIdAndTenantId(payslipId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payslip not found."));
        boolean owner = userId.equals(payslip.getRecipientUserId());
        if (!owner && !businessMembershipService.userHasAccessToBusiness(userId, payslip.getBusinessId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payslip not found.");
        }
        byte[] content = s3Service.downloadFile(payslip.getFileUrl());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(payslip.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(payslip.getFileName()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(content);
    }

    private Optional<CustomerProfile> findRegisteredCustomer(String identifier, Long tenantId) {
        Optional<CustomerProfile> found = customerProfileRepository
            .findFirstByEmailIgnoreCaseAndTenantIdAndUserIdIsNotNull(identifier, tenantId);
        if (found.isEmpty()) {
            found = customerProfileRepository.findFirstByPhoneAndTenantIdAndUserIdIsNotNull(identifier, tenantId);
        }
        if (found.isEmpty()) {
            found = customerProfileRepository
                .findFirstByProfileIdIgnoreCaseAndTenantIdAndUserIdIsNotNull(identifier, tenantId);
        }
        return found;
    }

    private BusinessEmployee findEmployee(Long businessId, Long employeeId) {
        return employeeRepository.findByIdAndTenantIdAndBusinessId(employeeId, TenantContext.getTenantId(), businessId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
    }

    private Map<Long, CustomerProfile> profilesById(Long tenantId, List<BusinessEmployee> employees) {
        List<Long> ids = employees.stream().map(BusinessEmployee::getCustomerProfileId).distinct().toList();
        return customerProfileRepository.findAllById(ids).stream()
            .filter(profile -> tenantId.equals(profile.getTenantId()))
            .collect(Collectors.toMap(CustomerProfile::getId, Function.identity()));
    }

    private Long requireUser() {
        Long userId = TenantContext.getUserId();
        if (TenantContext.getTenantId() == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated.");
        }
        return userId;
    }

    private Business requireManager(Long businessId) {
        Long userId = requireUser();
        Long tenantId = TenantContext.getTenantId();
        Business business = businessRepository.findById(businessId)
            .filter(found -> tenantId.equals(found.getTenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found."));
        if (!businessMembershipService.userHasAccessToBusiness(userId, businessId, tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage this business.");
        }
        return business;
    }

    private Business requireEnabledManager(Long businessId) {
        Business business = requireManager(businessId);
        if (!business.isPayslipsEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payslips are not enabled for this business.");
        }
        return business;
    }

    private static EmployeeView toView(BusinessEmployee employee, CustomerProfile profile) {
        return new EmployeeView(employee.getId(), employee.getBusinessId(), employee.getCustomerProfileId(),
            profile == null ? null : profile.getName(), profile == null ? null : profile.getEmail(),
            profile == null ? null : profile.getPhone(), employee.getEmployeeCode(), employee.getDesignation(),
            employee.getCreatedAt());
    }

    private static PayslipView toView(Payslip p, String businessName, String employeeName) {
        return new PayslipView(p.getId(), p.getBusinessId(), businessName, p.getEmployeeId(), employeeName,
            p.getPayMonth(), p.getPayYear(), period(p.getPayMonth(), p.getPayYear()), p.getNote(), p.getFileName(),
            p.getContentType(), p.getCreatedAt());
    }

    private static String period(int month, int year) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String safeFileName(String original, String contentType) {
        String ext = "application/pdf".equals(contentType) ? ".pdf" : "image/png".equals(contentType) ? ".png" : ".jpg";
        String base = original == null ? "payslip" : original.replaceAll("^.*[\\\\/]", "").replaceAll("\\.[^.]*$", "");
        base = base.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (base.isEmpty()) {
            base = "payslip";
        }
        return (base.length() > 80 ? base.substring(0, 80) : base) + ext;
    }
}
