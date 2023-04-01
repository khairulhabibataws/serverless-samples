package com.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IntegrationTest {

    @Test
    public void integrationFirst(){
        String integration = "True";
        Assertions.assertNotNull(integration);
    }
    
}
