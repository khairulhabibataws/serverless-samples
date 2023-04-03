package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.appsync.AppSyncClient;
import software.amazon.awssdk.services.appsync.model.EvaluateMappingTemplateRequest;
import software.amazon.awssdk.services.appsync.model.EvaluateMappingTemplateResponse;

public class MockTest {

    private static String locationContext = "{\"arguments\":\n    {\n        \"locationid\": \"1234567890\",\n        \"name\": \"Location Name\",\n        \"description\": \"Location Description\",\n        \"imageUrl\": \"https://www.example.com/image.jpg\"\n    },\n\"result\": {\n    \"locationid\": \"1234567890\",\n    \"imageUrl\": \"https://www.example.com/image.jpg\",\n    \"name\": \"Location Name\",\n    \"description\": \"Location Description\",\n    \"timestamp\": \"2023-01-01T00:00:00.000Z\"\n}}";
    private static String resourceContext = "{\n        \"arguments\":\n            {\n                \"resourceid\": \"1234567890\",\n                \"locationid\": \"abcdefghij\",\n                \"name\": \"Resource Name\",\n                \"description\": \"Resource Description\",\n                \"type\": \"Resource Type\"\n            },\n        \"result\":\n            {\n                \"resourceid\": \"1234567890\",\n                \"locationid\": \"abcdefghij\",\n                \"name\": \"Resource Name\",\n                \"description\": \"Resource Description\",\n                \"type\": \"Resource Type\",\n                \"timestamp\": \"2023-01-01T00:00:00.000Z\"\n            }\n    }";

    private AppSyncClient appClient;
    private String currentDir = System.getProperty("user.dir");

    @BeforeEach
    private void beforeEach(){
        try {
            appClient = AppSyncClient.builder().build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    @Test
    public void testFirst(){
        String testString = "halo";
        Assertions.assertNotNull(testString);
    }

    @Test
    public void testCreateLocationResolverWithLocationId(){
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode contextMap = mapper.readTree(locationContext);

            // Test request mapping template
            EvaluateMappingTemplateRequest templateRequest = EvaluateMappingTemplateRequest.builder()
                .template(getTemplateToString("create_location_request.vtl"))
                .context(locationContext)
                .build();
            EvaluateMappingTemplateResponse templateResponseRequest = appClient.evaluateMappingTemplate(templateRequest);
            Assertions.assertNotNull(templateResponseRequest.evaluationResult());
            
            JsonNode resultMapRequest = mapper.readTree(templateResponseRequest.evaluationResult());
            
            Assertions.assertEquals("PutItem", resultMapRequest.get("operation").asText());
            Assertions.assertEquals(contextMap.get("arguments").get("locationid"), resultMapRequest.get("key").get("locationid").get("S"));

            // Test response mapping response
            EvaluateMappingTemplateRequest templateResponse = EvaluateMappingTemplateRequest.builder()
                .template(getTemplateToString("create_location_response.vtl"))
                .context(locationContext)
                .build();
            EvaluateMappingTemplateResponse templateResponseResponse = appClient.evaluateMappingTemplate(templateResponse);
            Assertions.assertNotNull(templateResponseResponse.evaluationResult());

            JsonNode resultMapResponse = mapper.readTree(templateResponseResponse.evaluationResult());
            for (JsonNode jsonNode : resultMapResponse) {
                Assertions.assertEquals(contextMap.get("result").get(jsonNode.asText()), resultMapResponse.get(jsonNode.asText()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    private String getTemplateToString(String location) throws IOException{

        try {
            Path path = Paths.get(currentDir,"src","mapping_templates",location);
            String content = new String(Files.readAllBytes(path));
            System.out.println("vtl > " + content);
            return content;
        } catch (IOException e) {
            throw e;
        }
        
    }
}
