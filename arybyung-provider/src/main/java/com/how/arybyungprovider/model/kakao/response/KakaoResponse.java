package com.how.arybyungprovider.model.kakao.response;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoResponse {
    @SerializedName("version")
    @Expose
    public String version;
    @SerializedName("template")
    @Expose
    public KakaoResponseTemplate template;
}
