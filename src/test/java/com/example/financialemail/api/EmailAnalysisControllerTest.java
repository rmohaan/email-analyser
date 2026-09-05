package com.example.financialemail.api;

import com.example.financialemail.service.EmlParser;
import com.example.financialemail.service.EmailAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailAnalysisController.class)
class EmailAnalysisControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailAnalysisService emailAnalysisService;

    @MockBean
    private EmlParser emlParser;

    @Test
    void rejectsAnEmptyEmlFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "message.eml", "message/rfc822", new byte[0]);
        when(emlParser.parse(file)).thenThrow(new InvalidEmailFileException("A non-empty .eml file is required"));

        mockMvc.perform(multipart("/api/v1/email-analysis").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email file"));
    }

    @Test
    void rejectsARequestWithoutAFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/email-analysis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email file"));
    }
}
