package com.devops.devops2026.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(Controller.class)
class ControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Should return Hello!")
    void hello_ShouldReturnOkAndGreeting() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string("Hello!"));
    }

    void test_ShouldReturnProvidedName() throws Exception {
        String testName = "Alice";
        mvc.perform(get("/test/{name}", testName))
            .andExpect(status().isOk())
            .andExpect(content().string(testName));
    }

}
