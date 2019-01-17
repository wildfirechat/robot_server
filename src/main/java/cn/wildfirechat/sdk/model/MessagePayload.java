package cn.wildfirechat.sdk.model;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class MessagePayload {
    private int type;
    private String searchableContent;
    private String pushContent;
    private String content;
    private String base64edData;
    private int mediaType;
    private String remoteMediaUrl;
    private int persistFlag;
    private int expireDuration;
    private int mentionedType;
    private List<String> mentionedTarget;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getSearchableContent() {
        return searchableContent;
    }

    public void setSearchableContent(String searchableContent) {
        this.searchableContent = searchableContent;
    }

    public String getPushContent() {
        return pushContent;
    }

    public void setPushContent(String pushContent) {
        this.pushContent = pushContent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBase64edData() {
        return base64edData;
    }

    public void setBase64edData(String base64edData) {
        this.base64edData = base64edData;
    }

    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public String getRemoteMediaUrl() {
        return remoteMediaUrl;
    }

    public void setRemoteMediaUrl(String remoteMediaUrl) {
        this.remoteMediaUrl = remoteMediaUrl;
    }

    public int getPersistFlag() {
        return persistFlag;
    }

    public void setPersistFlag(int persistFlag) {
        this.persistFlag = persistFlag;
    }

    public int getExpireDuration() {
        return expireDuration;
    }

    public void setExpireDuration(int expireDuration) {
        this.expireDuration = expireDuration;
    }

    public int getMentionedType() {
        return mentionedType;
    }

    public void setMentionedType(int mentionedType) {
        this.mentionedType = mentionedType;
    }

    public List<String> getMentionedTarget() {
        return mentionedTarget;
    }

    public void setMentionedTarget(List<String> mentionedTarget) {
        this.mentionedTarget = mentionedTarget;
    }
}
