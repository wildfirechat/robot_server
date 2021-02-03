package cn.wildfirechat.app.tuling;

import java.util.List;

public class TulingResponse {
    public static class Intent {
        int code;
        String intentName;
        String actionName;
    }
    public static class Value {
        String url;
        String text;
    }
    public static class Result {
        int groupType;
        String resultType;
        Value values;
    }
    public Intent intent;
    public List<Result> results;
}
