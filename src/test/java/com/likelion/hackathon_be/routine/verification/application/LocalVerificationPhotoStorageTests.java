package com.likelion.hackathon_be.routine.verification.application;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalVerificationPhotoStorageTests {
    private final LocalVerificationPhotoStorage storage = new LocalVerificationPhotoStorage();

    @Test
    void storesOnlyDefensiveInMemoryCopyAndZeroizesItOnDelete() {
        byte[] upload = {1, 2, 3, 4};
        MockMultipartFile photo = new MockMultipartFile("photo", "proof.png", "image/png", upload);

        StoredVerificationPhoto stored = storage.store(photo);

        upload[0] = 9;
        assertThat(stored.image()).containsExactly(1, 2, 3, 4);
        assertThat(stored.mediaType()).isEqualTo("image/png");

        byte[] exposed = stored.image();
        exposed[0] = 8;
        assertThat(stored.image()).containsExactly(1, 2, 3, 4);

        storage.delete(stored);
        assertThat(stored.image()).containsOnly((byte) 0);
    }

    @Test
    void rejectsDeclaredOversizeBeforeOpeningOrCopyingUploadStream() throws Exception {
        MultipartFile photo = mock(MultipartFile.class);
        when(photo.isEmpty()).thenReturn(false);
        when(photo.getSize()).thenReturn((long) PhotoVerificationInput.MAX_IMAGE_BYTES + 1);

        assertThatThrownBy(() -> storage.store(photo))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(photo, never()).getInputStream();
    }

    @Test
    void boundedReadRejectsStreamThatExceedsItsDeclaredSize() throws Exception {
        MultipartFile photo = mock(MultipartFile.class);
        when(photo.isEmpty()).thenReturn(false);
        when(photo.getSize()).thenReturn(1L);
        when(photo.getInputStream()).thenReturn(new ByteArrayInputStream(
                new byte[PhotoVerificationInput.MAX_IMAGE_BYTES + 1]
        ));

        assertThatThrownBy(() -> storage.store(photo))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
