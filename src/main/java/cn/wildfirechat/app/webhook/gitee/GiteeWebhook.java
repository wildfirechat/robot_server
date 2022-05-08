package cn.wildfirechat.app.webhook.gitee;

import cn.wildfirechat.app.webhook.IWebhook;
import cn.wildfirechat.app.webhook.gitee.pojo.*;
import cn.wildfirechat.pojos.MessagePayload;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

public class GiteeWebhook implements IWebhook {
    private static final Logger LOG = LoggerFactory.getLogger(GiteeWebhook.class);
    @Override
    public String invokeCommand() {
        return "/gitee";
    }

    @Override
    public Object handleWebhookPost(HttpServletRequest request, String user, String githubPayload, SendMessageCallback callback) {
        String event = request.getHeader("X-Gitee-Event");
        LOG.info("on receive message {}, event {}", githubPayload, event);

        String message = null;

        //https://gitee.com/help/articles/4271#article-header0
        MessagePayload messagePayload = new MessagePayload();
        messagePayload.setType(1);
        try {
            if (event.equals("Push Hook")) {
                PushOrTagHook pushOrTagHook = new Gson().fromJson(githubPayload, PushOrTagHook.class);
                message = pushOrTagHook.sender.name + " push " + pushOrTagHook.total_commits_count + " commits";
                if (pushOrTagHook.commits != null) {
                    for (Commit commit : pushOrTagHook.commits) {
                        message += "\nCommit url: " + commit.url;
                        message += "\nCommit message: " + commit.message;
                        message += "\n";
                    }
                }
            } else if(event.equals("Tag Push Hook")) {
                PushOrTagHook pushOrTagHook = new Gson().fromJson(githubPayload, PushOrTagHook.class);
                message = pushOrTagHook.sender.name + (pushOrTagHook.created ?  "创建了" : (pushOrTagHook.deleted ? "删除了":"更新了")) + " tag.";
                message += "\nTag: " + pushOrTagHook.compare;
                message += "\nRef: " + pushOrTagHook.ref;
            } else if(event.equals("Issue Hook")) {
                IssueHook issueHook = new Gson().fromJson(githubPayload, IssueHook.class);
                message = issueHook.sender.name + " " + issueHook.action + " issue.";
                message += "\nIssue:"  + issueHook.issue.html_url;
                message += "\nMessage: " + issueHook.description;
                message += "\nStatus: " + issueHook.state;
            } else if(event.equals("Note Hook")) {
                NoteHook noteHook = new Gson().fromJson(githubPayload, NoteHook.class);
                message = noteHook.sender.name + " " + noteHook.action + " issue";
                message += "\nIssue:"  + noteHook.issue.html_url;
                message += "\nMessage: " + noteHook.note;
                message += "\nStatus: " + noteHook.issue.state;
            } else if(event.equals("Pull Request Hook")) {
                PullRequestHook pullRequestHook = new Gson().fromJson(githubPayload, PullRequestHook.class);
                message = pullRequestHook.sender.name + " " + pullRequestHook.action + "PR";
                message += "\nPR: " + pullRequestHook.url;
                message += "\nTitle: " + pullRequestHook.title;
                message += "\nBody: " + pullRequestHook.body;
            }
        } catch (Exception e) {
            e.printStackTrace();
            messagePayload.setSearchableContent("糟糕，处理gitee事件出错了：" + e.getMessage());
            callback.sendMessage(messagePayload);
        }

        if (message == null) {
            messagePayload.setSearchableContent("你收到了一个gitee事件：" + event);
            callback.sendMessage(messagePayload);

            messagePayload.setSearchableContent(githubPayload);
            callback.sendMessage(messagePayload);
        } else {
            messagePayload.setSearchableContent(message);
            callback.sendMessage(messagePayload);
        }
        return "ok";
    }
}
