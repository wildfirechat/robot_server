package cn.wildfirechat.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ConfigurationProperties(prefix="robot")
@PropertySource(value = "file:config/robot.properties", encoding = "UTF-8")
public class RobotConfig {
    public String im_url;
    public String im_secret;
    public String im_id;
    public String im_name;

    public boolean use_tuling;

    public String tuling_key;

    public String public_addr;

    public boolean isUse_tuling() {
        return use_tuling;
    }

    public void setUse_tuling(boolean use_tuling) {
        this.use_tuling = use_tuling;
    }

    public String getTuling_key() {
        return tuling_key;
    }

    public void setTuling_key(String tuling_key) {
        this.tuling_key = tuling_key;
    }

    public String getIm_name() {
        return im_name;
    }

    public void setIm_name(String im_name) {
        this.im_name = im_name;
    }

    public String getIm_id() {
        return im_id;
    }

    public void setIm_id(String im_id) {
        this.im_id = im_id;
    }

    public String getIm_url() {
        return im_url;
    }

    public void setIm_url(String im_url) {
        this.im_url = im_url;
    }

    public String getIm_secret() {
        return im_secret;
    }

    public void setIm_secret(String im_secret) {
        this.im_secret = im_secret;
    }

    public String getPublic_addr() {
        return public_addr;
    }

    public void setPublic_addr(String public_addr) {
        this.public_addr = public_addr;
    }
}
