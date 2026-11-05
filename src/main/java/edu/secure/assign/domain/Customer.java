package edu.secure.assign.domain;

/** DB 원본 객체 */
public class Customer {

    private String customerId;
    private String customerName;
    private String rrn;
    private String phone;
    private String passwordHash;

    public Customer() {
    }

    public Customer(String customerId, String customerName, String rrn, String phone, String passwordHash) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.rrn = rrn;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    @Override
    public String toString() {
        return "Customer{customerId='" + customerId + "', customerName='" + customerName
                + "', rrn='" + rrn + "', phone='" + phone + "', passwordHash='" + passwordHash + "'}";
    }
}
