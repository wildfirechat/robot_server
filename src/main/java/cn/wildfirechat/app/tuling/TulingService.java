package cn.wildfirechat.app.tuling;

import cn.wildfirechat.app.HttpUtils;
import cn.wildfirechat.app.RobotConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TulingService {
    @Autowired
    private RobotConfig mRobotConfig;

    public String handleWord(String sender, String text) {
        String response = "";
        if (mRobotConfig.use_tuling) {
            String searchReq = "{\n" + "\t\"reqType\":0,\n" + "    \"perception\": {\n" + "        \"inputText\": {\n" + "            \"text\": \"${TEXT}\"\n" + "        }\n" + "    },\n" + "    \"userInfo\": {\n" + "        \"apiKey\": \"${APIKEY}\",\n" + "        \"userId\": \"${USERID}\"\n" + "    }\n" + "}";
            searchReq = searchReq.replace("${APIKEY}", mRobotConfig.getTuling_key()).replace("${USERID}", Math.abs(sender.hashCode()) + "");
            searchReq = searchReq.replace("${TEXT}", text);


            try {
                TulingResponse s = HttpUtils.post("http://openapi.tuling123.com/openapi/api/v2", searchReq, TulingResponse.class);
                if (s != null) {
                    if (s.results != null && s.results.size() > 0) {
                        for (TulingResponse.Result result : s.results
                        ) {
                            if (result.values != null) {
                                if (!StringUtils.isEmpty(result.values.text)) {
                                    if(StringUtils.isEmpty(response)) {
                                        response = result.values.text;
                                    } else {
                                        response = response + " \n" + result.values.text;
                                    }
                                }

                                if (!StringUtils.isEmpty(result.values.url)) {
                                    if(StringUtils.isEmpty(response)) {
                                        response = result.values.url;
                                    } else {
                                        response = response + " \n" + result.values.url;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return response;
    }
}
