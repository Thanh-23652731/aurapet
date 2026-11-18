package com.aurapet.backend.domain.model;

/** Giới hạn ảnh mẫu Re-ID. */
public final class PetReidPhotoLimits {
    /** Mỗi ảnh tối đa 1 MB. */
    public static final int MAX_BYTES = 1024 * 1024;
    /** Tối đa ảnh mẫu mỗi bé (đủ góc nhìn; nhiều hơn chỉ làm Pi so khớp chậm hơn). */
    public static final int MAX_PER_PET = 40;
    /** Nên có ít nhất chừng này ảnh để nhận ra ổn định. */
    public static final int MIN_RECOMMENDED = 5;

    private PetReidPhotoLimits() {
    }
}
