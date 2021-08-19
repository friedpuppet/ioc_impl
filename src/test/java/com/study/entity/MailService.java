package com.study.entity;

import javax.annotation.PostConstruct;

public class MailService implements IMailService {
    private String protocol;
    private int port;

    @PostConstruct
    private void init() {
        port = port * 2;
        // make some initialization
        // fill cache
    }

    @Override
    public void sendEmail(User user, String message) {
        System.out.println("sending email with message: " + message);
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public int getPort() {
        return port;
    }
}
