package com.bhive.bills.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "provider_input_fields",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_provider_input_field",
        columnNames = {"provider_id", "field_key"}
    )
)
public class ProviderInputField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private BillProvider provider;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "input_type", nullable = false, length = 30)
    private String inputType;

    @Column(nullable = false)
    private boolean required;

    @Column(length = 200)
    private String placeholder;

    @Column(name = "help_text", length = 500)
    private String helpText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    protected ProviderInputField() {
    }

    public ProviderInputField(
            BillProvider provider,
            String fieldKey,
            String label,
            String inputType,
            boolean required,
            String placeholder,
            String helpText,
            Integer displayOrder) {
        this.provider = provider;
        this.fieldKey = fieldKey;
        this.label = label;
        this.inputType = inputType;
        this.required = required;
        this.placeholder = placeholder;
        this.helpText = helpText;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public BillProvider getProvider() {
        return provider;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getLabel() {
        return label;
    }

    public String getInputType() {
        return inputType;
    }

    public boolean isRequired() {
        return required;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getHelpText() {
        return helpText;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
    public void setLabel(String label) {
    this.label = label;
}

public void setInputType(String inputType) {
    this.inputType = inputType;
}

public void setRequired(boolean required) {
    this.required = required;
}

public void setPlaceholder(String placeholder) {
    this.placeholder = placeholder;
}

public void setHelpText(String helpText) {
    this.helpText = helpText;
}

public void setDisplayOrder(Integer displayOrder) {
    this.displayOrder = displayOrder;
}

public void setActive(boolean active) {
    this.active = active;
}
}