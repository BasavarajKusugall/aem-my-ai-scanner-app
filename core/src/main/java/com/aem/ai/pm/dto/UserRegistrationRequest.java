package com.aem.ai.pm.dto;

public class UserRegistrationRequest {
    private AppUser user;
    private UserBrokerAccount account;
    private BrokerToken token;

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public UserBrokerAccount getAccount() { return account; }
    public void setAccount(UserBrokerAccount account) { this.account = account; }
    public BrokerToken getToken() { return token; }
    public void setToken(BrokerToken token) { this.token = token; }



    @Override
    public String toString() {
        return "UserRegistrationRequest{" +
                "user=" + user +
                ", account=" + account +
                ", token=" + token +
                '}';
    }


}
