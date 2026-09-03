package com.example.financialemail.api;

import com.example.financialemail.service.EmailAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailAnalysisController.class)
class EmailAnalysisControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailAnalysisService emailAnalysisService;

    @Test
    void rejectsBlankEmail() throws Exception {
        mockMvc.perform(post("/api/v1/email-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailBody\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }
}
