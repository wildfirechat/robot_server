package cn.wildfirechat.app.service;

import cn.wildfirechat.app.jpa.*;
import cn.wildfirechat.app.jpa.Thread;
import cn.wildfirechat.messagecontentbuilder.RichNotificationContentBuilder;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.SendMessageResult;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.IMResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class ScheduledTasks {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledTasks.class);

    @Value("${im.url}")
    private String imUrl;

    @Value("${robot.secret}")
    private String robotSecret;

    @Value("${robot.id}")
    private String robotId;

    @Value("${notify.conversation.type}")
    private int conversationType;

    @Value("${notify.conversation.target}")
    private String conversationTarget;

    private int previousPid = 0;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ThreadRepository threadRepository;

    @Autowired
    private UserRepository userRepository;

    private String[] finishWord = {/*"Ok","明白了","可以了","ok了","可以","好的，谢谢","好的","谢谢"*/};

    private static final Map<Integer, String> userNameCache = new ConcurrentHashMap<>();

    private RobotService robotService;

    @PostConstruct
    private void init() {
        robotService = new RobotService(imUrl, robotId, robotSecret);
    }


    @Scheduled(initialDelay =  10000, fixedDelay = 30000)
    public void checkPost() {
        List<Integer> managerUids = new ArrayList<>();
        managerUids.add(12);
        managerUids.add(14);

        List<Post> ps = postRepository.getLatestPosts(100);
        Map<Integer, List<Post>> threadMap = new HashMap<>();
        if(!ps.isEmpty()) {
            for (Post p : ps) {
                List<Post> threadPosts = threadMap.computeIfAbsent(p.tid, i -> new ArrayList<>());
                threadPosts.add(p);
            }

            threadMap.forEach((integer, posts) -> {
                boolean finished = false;
                if(managerUids.contains(posts.get(0).uid)) {
                    finished = true;
                } else {
                    for (String s : finishWord) {
                        if(s.equalsIgnoreCase(posts.get(0).message)) {
                            finished = true;
                            break;
                        }
                    }
                }

                if(!finished) {
                    notifyUser(posts);
                }
            });
        }
    }

    private String getUserName(int uid) {
        if(userNameCache.containsKey(uid)) {
            return userNameCache.get(uid);
        }
        Optional<User> optionalUser = userRepository.findById(uid);
        if(optionalUser.isPresent()) {
            userNameCache.put(uid, optionalUser.get().username);
            return optionalUser.get().username;
        }
        return "未知用户";
    }

    private void notifyUser(List<Post> posts) {
        Optional<Thread> thread = threadRepository.findById(posts.get(0).tid);
        if (!thread.isPresent()) {
            return;
        }

        Conversation conversation = new Conversation();
        conversation.setType(conversationType);
        conversation.setTarget(conversationTarget);

        RichNotificationContentBuilder builder = RichNotificationContentBuilder.newBuilder("论坛消息提醒", thread.get().subject, "https://bbs.wildfirechat.cn/thread-" + thread.get().tid + ".htm")
                .remark("请及时处理")
                .exName("野火论坛小助手");
        posts.forEach(post -> builder.addItem(getUserName(post.uid), post.message, "#173177"));

        try {
            IMResult<SendMessageResult> result = robotService.sendMessage(robotId, conversation, builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}