package com.ecommerce.platform.media;

import com.ecommerce.platform.modules.users.model.User;
import com.ecommerce.platform.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
public class ImageUploadControllerIT extends IntegrationTestBase {

    private static byte[] validPngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    }

    @Test
    void upload_requires_admin_or_manager() throws Exception {
        User user = createUser("basicupload@example.com", "secret123", "ROLE_USER");
        String token = tokenFor(user);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                validPngBytes()
        );

        mockMvc.perform(multipart("/api/v1/images/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_success_as_admin() throws Exception {
        User admin = createUser("adminupload@example.com", "secret123", "ROLE_ADMIN");
        String token = tokenFor(admin);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                validPngBytes()
        );

        mockMvc.perform(multipart("/api/v1/images/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("http://localhost:8080/images/")));
    }

    @Test
    void upload_rejects_invalid_signature() throws Exception {
        User admin = createUser("adminupload2@example.com", "secret123", "ROLE_ADMIN");
        String token = tokenFor(admin);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                "not-a-real-image".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/images/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
