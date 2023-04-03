package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetRandomPasswordRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetRandomPasswordResponse;

public class IntegrationTest {

    private static Map<String,String> globalConfigMap = new HashMap<>();
    private static CloudFormationClient cfClient;
    private static CognitoIdentityClient cognitoClient;
    private static CognitoIdentityProviderClient idpClient;
    private static DynamoDbClient dbClient;
    private static SecretsManagerClient smClient;

    static String jsonNewLocation = "{\"imageUrl\": \"https://api.example.com/venetian.jpg\",\n    \"description\": \"Headquarters in New York\",\n    \"name\": \"HQ\"}";

    private static JsonNode newLocation;

    @BeforeAll
    private static void init(){
        try {
            ObjectMapper mapper = new ObjectMapper();
            cfClient = CloudFormationClient.builder().build();
            cognitoClient = CognitoIdentityClient.builder().build();
            idpClient = CognitoIdentityProviderClient.builder().build();
            smClient = SecretsManagerClient.builder().build();
            dbClient = DynamoDbClient.builder().build();
            String applicationStackName = System.getenv().getOrDefault("TEST_APPLICATION_STACK_NAME", "java-appsync-sam");
            String cognitoStackName = System.getenv().getOrDefault("TEST_COGNITO_STACK_NAME", "serverless-api-cognito");
            getStackOutputs(applicationStackName);
            getStackOutputs(cognitoStackName);
            createCognitoAccounts();
            clearDynamoDBTables();
            newLocation = mapper.readTree(jsonNewLocation);
        } catch (Exception e) {
            System.err.println(e);
        }
        
    }


    private static void getStackOutputs(String stackName){
        DescribeStacksResponse response = cfClient.describeStacks(DescribeStacksRequest.builder().stackName(stackName).build());
        response.stacks().forEach(stack -> {
            stack.outputs().forEach(output ->{
                System.out.println(output.outputKey() + "," + output.outputValue());
                globalConfigMap.put(output.outputKey(), output.outputValue());
            });
        });
    }

    private static void createCognitoAccounts(){

        // Create regular user account
        GetRandomPasswordResponse response = smClient.getRandomPassword(GetRandomPasswordRequest.builder()
                                                .excludeCharacters("\"'`[]{}():;,$/\\<>|=&")
                                                .requireEachIncludedType(true)
                                                .build());
        globalConfigMap.put("regularUserName", "regularUser@example.com");
        globalConfigMap.put("regularUserPassword", response.randomPassword());

        try {
            idpClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                        .userPoolId(globalConfigMap.get("UserPool"))
                        .username(globalConfigMap.get("regularUserName"))
                        .build());
            
        } catch (Exception e) {
            System.out.println("Regular user haven't been created previously");
        }

        Collection<AttributeType> userAttributes = new ArrayList<>();
        userAttributes.add(AttributeType.builder()
                            .name("name")
                            .value(globalConfigMap.get("regularUserName"))
                            .build());
        
        SignUpResponse signUpResponse = idpClient.signUp(SignUpRequest.builder()
                    .clientId(globalConfigMap.get("UserPoolClient"))
                    .username(globalConfigMap.get("regularUserName"))
                    .password(globalConfigMap.get("regularUserPassword"))
                    .userAttributes(userAttributes)
                    .build());
        globalConfigMap.put("regularUserSub", signUpResponse.userSub());

        idpClient.adminConfirmSignUp(AdminConfirmSignUpRequest.builder()
            .userPoolId(globalConfigMap.get("UserPool"))
            .username(globalConfigMap.get("regularUserName"))
            .build());
        
        Map<String,String> authParam = new HashMap<>();
        authParam.put("USERNAME", globalConfigMap.get("regularUserName"));
        authParam.put("PASSWORD", globalConfigMap.get("regularUserPassword"));

        // Get new user authentication info
        InitiateAuthResponse authResponse = idpClient.initiateAuth(InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .authParameters(authParam)
                    .clientId(globalConfigMap.get("UserPoolClient"))
                    .build());
        
        globalConfigMap.put("regularUserIdToken", authResponse.authenticationResult().idToken());
        globalConfigMap.put("regularUserAccessToken", authResponse.authenticationResult().accessToken());
        globalConfigMap.put("regularUserRefreshToken", authResponse.authenticationResult().refreshToken());

        // Create Administrative User Account
        GetRandomPasswordResponse adminPasswordResponse = smClient.getRandomPassword(GetRandomPasswordRequest.builder()
                    .excludeCharacters("\"'`[]{}():;,$/\\<>|=&")
                    .requireEachIncludedType(true)
                    .build());
        globalConfigMap.put("adminUserName", "adminUser@example.com");
        globalConfigMap.put("adminUserPassword", adminPasswordResponse.randomPassword());

        try {
            idpClient.adminDeleteUser(AdminDeleteUserRequest.builder()
            .userPoolId(globalConfigMap.get("UserPool"))
            .username(globalConfigMap.get("adminUserName")).build());
        } catch (Exception e) {
            System.err.println("Regular user haven't been created previously");
        }

        SignUpResponse adminSignupResponse = idpClient.signUp(SignUpRequest.builder()
            .clientId(globalConfigMap.get("UserPoolClient"))
            .username(globalConfigMap.get("adminUserName"))
            .password(globalConfigMap.get("adminUserPassword"))
            .userAttributes(AttributeType.builder().name("name").value(globalConfigMap.get("adminUserName")).build())
            .build());

        globalConfigMap.put("adminUserSub", adminSignupResponse.userSub());
        idpClient.adminConfirmSignUp(AdminConfirmSignUpRequest.builder()
            .userPoolId(globalConfigMap.get("UserPool"))
            .username(globalConfigMap.get("adminUserName"))
            .build());
            
        // Add administrative user to the admins group
        idpClient.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
        .userPoolId(globalConfigMap.get("UserPool"))
        .username(globalConfigMap.get("adminUserName"))
        .groupName(globalConfigMap.get("UserPoolAdminGroupName"))
        .build());
        
        // Get new admin user authentication info
        Map<String,String> adminAuthMap = new HashMap<>();
        adminAuthMap.put("USERNAME", globalConfigMap.get("adminUserName"));
        adminAuthMap.put("PASSWORD", globalConfigMap.get("adminUserPassword"));
        InitiateAuthResponse adminAuthResponse = idpClient.initiateAuth(InitiateAuthRequest.builder()
        .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
        .authParameters(adminAuthMap)
        .clientId(globalConfigMap.get("UserPoolClient"))
        .build());

        globalConfigMap.put("adminUserIdToken", adminAuthResponse.authenticationResult().idToken());
        globalConfigMap.put("adminUserAccessToken", adminAuthResponse.authenticationResult().accessToken());
        globalConfigMap.put("adminUserIdToken", adminAuthResponse.authenticationResult().refreshToken());
    }

    private static void clearDynamoDBTables(){
        // Clear locations table
        ScanResponse locationScanResponse = dbClient.scan(ScanRequest.builder()
            .tableName(globalConfigMap.get("LocationsTable"))
            .attributesToGet("locationid")
            .build());
        locationScanResponse.items().forEach(item->{
            dbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(globalConfigMap.get("LocationsTable"))
                .key(item)
                .build());
        });

        // Clear resources table
        ScanResponse resourcesResponse = dbClient.scan(ScanRequest.builder()
        .tableName(globalConfigMap.get("ResourcesTable"))
        .attributesToGet("resourceid")
        .build());
        resourcesResponse.items().forEach(item->{
            dbClient.deleteItem(DeleteItemRequest.builder()
            .tableName(globalConfigMap.get("ResourcesTable"))
            .key(item)
            .build());
        });

        // Clear bookings table
        ScanResponse bookingsResponse = dbClient.scan(ScanRequest.builder()
        .tableName(globalConfigMap.get("BookingsTable"))
        .attributesToGet("bookingid")
        .build());
        bookingsResponse.items().forEach(item->{
            dbClient.deleteItem(DeleteItemRequest.builder()
            .tableName(globalConfigMap.get("BookingsTable"))
            .key(item)
            .build());
        });
    }

    

    @Test
    public void integrationFirst(){
        String integration = "True";
        Assertions.assertNotNull(integration);
    }

    @Test
    public void testAccessToApiWithoutAuthentication() throws IOException{
        String schemaRequest = "{\"query\":\"{__schema {queryType {fields {name}}}}\",\"variables\":{}}";
        StringEntity entity = new StringEntity(schemaRequest);
        final HttpPost httpPost = new HttpPost(globalConfigMap.get("APIEndpoint"));
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-Type", "application/json");
        try(CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(httpPost)){
                final int statusCode = response.getStatusLine().getStatusCode();
                assertEquals(HttpStatus.SC_UNAUTHORIZED, statusCode);
            }
    }

    @Test
    public void testAccessToApiWithAuthentication() throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        String schemaRequest = "{\"query\":\"{__schema {queryType {fields {name}}}}\",\"variables\":{}}";
        StringEntity entity = new StringEntity(schemaRequest);
        final HttpPost httpPost = new HttpPost(globalConfigMap.get("APIEndpoint"));
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Authorization",globalConfigMap.get("adminUserAccessToken"));
        try(CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(httpPost)){
                final int statusCode = response.getStatusLine().getStatusCode();
                assertEquals(HttpStatus.SC_OK, statusCode);
                String stringResponse = EntityUtils.toString(response.getEntity());
                JsonNode resultNode = mapper.readTree(stringResponse);
                System.out.println(stringResponse);
                assertNotNull(resultNode.get("data")
                    .get("__schema")
                    .get("queryType")
                    .get("fields"));
            }
    }

    @Test
    public void testGetListOfLocationsByRegularUser() throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        String schemaRequest = "{\"query\":\"query allLocations {getAllLocations {description name imageUrl resources {description name type bookings {starttimeepochtime userid}}}}\",\"variables\":{}}";
        StringEntity entity = new StringEntity(schemaRequest);
        final HttpPost httpPost = new HttpPost(globalConfigMap.get("APIEndpoint"));
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Authorization",globalConfigMap.get("regularUserAccessToken"));
        try(CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(httpPost)){
                final int statusCode = response.getStatusLine().getStatusCode();
                assertEquals(HttpStatus.SC_OK, statusCode);
                String stringResponse = EntityUtils.toString(response.getEntity());
                JsonNode resultNode = mapper.readTree(stringResponse);
                System.out.println(stringResponse);
                assertNotNull(resultNode.get("data")
                    .get("getAllLocations"));
            }
    }

    @Test
    public void testDenyPutLocationByRegularUser() throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        String schemaRequest = "{\"query\":\"mutation addLocation {createLocation(name: \\\""+newLocation.get("name").asText()+"\\\", description: \\\""+newLocation.get("description").asText()+"\\\", imageUrl: \\\""+newLocation.get("imageUrl").asText()+"\\\") {name locationid imageUrl description timestamp}}\",\"variables\":{}}";
        StringEntity entity = new StringEntity(schemaRequest);
        final HttpPost httpPost = new HttpPost(globalConfigMap.get("APIEndpoint"));
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Authorization",globalConfigMap.get("regularUserAccessToken"));
        try(CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(httpPost)){
                final int statusCode = response.getStatusLine().getStatusCode();
                assertEquals(HttpStatus.SC_OK, statusCode);
                String stringResponse = EntityUtils.toString(response.getEntity());
                JsonNode resultNode = mapper.readTree(stringResponse);
                System.out.println(stringResponse);
                assertEquals(NullNode.getInstance(),resultNode.get("data")
                    .get("createLocation"));
                assertEquals("Unauthorized", resultNode.get("errors")
                    .get(0)
                    .get("errorType").asText());
            }
    }
    
}
