package com.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HandlerTest {
    
    @Test
    public void testFirst(){
        String testString = "halo";
        Assertions.assertNotNull(testString);
    }
}
