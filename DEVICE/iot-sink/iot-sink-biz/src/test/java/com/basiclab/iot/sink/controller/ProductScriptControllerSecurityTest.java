package com.basiclab.iot.sink.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductScriptControllerSecurityTest {

    @Test
    void serverSideScriptManagementRequiresSuperAdminRole() {
        PreAuthorize preAuthorize = ProductScriptController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("@ss.hasRole('super_admin')", preAuthorize.value());
    }
}
