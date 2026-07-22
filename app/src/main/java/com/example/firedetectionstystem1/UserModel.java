package com.example.firedetectionstystem1;

public class UserModel {
    private String email;
    private String phone;
    private String role;
    private String uid;

    public UserModel() {
        // Required for Firestore
    }

    public UserModel(String uid, String email, String phone, String role) {
        this.uid = uid;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getUid() { return uid; }
}
