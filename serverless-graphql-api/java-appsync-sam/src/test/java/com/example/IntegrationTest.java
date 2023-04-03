package com.example;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;

public class IntegrationTest {

    private Map globalConfigMap = new HashMap<>();
    private CloudFormationClient cfClient;
    private CognitoIdentityClient cognitoClient;

    @BeforeAll
    private void init(){
        cfClient = CloudFormationClient.builder().build();
        cognitoClient = CognitoIdentityClient.builder().build();
        getStackOutputs("java-appsync-map");
    }


    private void getStackOutputs(String stackName){
        DescribeStacksResponse response = cfClient.describeStacks();
        response.stacks().forEach(stack -> {
            stack.outputs().forEach(output ->{
                System.out.println(output.outputKey() + "," + output.outputValue());
            });
        });
    }

    @Test
    public void integrationFirst(){
        String integration = "True";
        Assertions.assertNotNull(integration);
    }
    
}
