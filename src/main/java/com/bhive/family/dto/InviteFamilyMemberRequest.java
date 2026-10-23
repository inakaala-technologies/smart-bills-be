package com.bhive.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InviteFamilyMemberRequest {

	@NotBlank(message = "Email or phone number is required")
	@Size(max = 254)
	private String contact;

	public InviteFamilyMemberRequest() {
	}

	public String getContact() {
		return contact;
	}

	public void setContact(String contact) {
		this.contact = contact;
	}
}