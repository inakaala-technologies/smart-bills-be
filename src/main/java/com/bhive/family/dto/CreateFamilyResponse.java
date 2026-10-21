
package com.bhive.family.dto;

public class CreateFamilyResponse {

	private Long familyId;
	private String message;

	public CreateFamilyResponse() {
	}

	public CreateFamilyResponse(Long familyId, String message) {
		this.familyId = familyId;
		this.message = message;
	}

	public Long getFamilyId() {
		return familyId;
	}

	public void setFamilyId(Long familyId) {
		this.familyId = familyId;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
