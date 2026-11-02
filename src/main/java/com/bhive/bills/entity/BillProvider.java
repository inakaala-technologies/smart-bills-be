package com.bhive.bills.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "bill_providers",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_bill_provider_code",
        columnNames = "code"
    )
)
public class BillProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private BillCategory category;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected BillProvider() {}

    public BillProvider(String code, String name, BillCategory category) {
        this.code = code;
        this.name = name;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BillCategory getCategory() { return category; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setName(String name) {
    this.name = name;
}

public void setCategory(BillCategory category) {
    this.category = category;
}

public void setActive(boolean active) {
    this.active = active;
}
}