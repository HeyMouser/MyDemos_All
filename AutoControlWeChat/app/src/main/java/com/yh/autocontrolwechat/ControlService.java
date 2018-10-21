package com.yh.autocontrolwechat;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

public class ControlService extends AccessibilityService {
    private static final String TAG = "微信辅助服务结果";
    //微信包名
    private final static String WeChat_PNAME = "com.tencent.mm";
    //微信布局ID前缀
    private static final String BaseLayoutId = "com.tencent.mm:id/";
    //微信首页
    public static final String WECHAT_CLASS_LAUNCHUI = "com.tencent.mm.ui.LauncherUI";
    //微信聊天页面
    public static final String WECHAT_CLASS_CHATUI = "com.tencent.mm.ui.chatting.ChattingUI";


    public static boolean isSendSuccess; //true 发送完成，  false 开始发送，还没发送呢

    //微信版本                                6.7.3
    private String searchedittextid = "ji";  //ji
    private String searchlistviewid = "bp0"; // bp0

    /**
     * 聊天界面
     */
    private String chatuiedittextid = "aie"; //  aep
    private String chatuiusernameid = "j6";  //  j1
    private String chatuiswitchid = "aic";   //  aen


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        String className = event.getClassName().toString();
        Log.i(TAG, "event >> TYPE:" + event.getEventType());
        Log.i(TAG, "event >> ClassName:" + className);

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                if (WeChat_PNAME.equals(event.getPackageName().toString())) {
                    sendNotifacationReply(event);
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                //如果是主动发送消息，成功之后  就不能继续监听事件了
                //如果不是主动发送消息，那么 根本没必要监听这个事件，被动接收消息，都是监听通知栏变化直接跳转到聊天界面，监听TYPE_WINDOW_CONTENT_CHANGED即可
                if (isSendSuccess) {
                    return;
                }
                switch (className) {
                    case WECHAT_CLASS_LAUNCHUI:
                        handleFlow_clickSearch();
                        break;
                    case WECHAT_CLASS_CHATUI:
                        handleFlow_ChatUI();
                        break;
                }
                break;
        }
    }

    @Override
    public void onInterrupt() {

    }

    /**
     * 微信界面，点击title的搜索按钮
     */
    private void handleFlow_clickSearch() {
        try {
            //如果没有名字，说明不是主动发送的，就没有必要搜索了
            if (TextUtils.isEmpty(WechatUtils.NAME)) return;

            //调起微信之后，不管在什么页面，先查找返回键并点击：防止在其他页面查找不到搜索按钮
            Thread.sleep(100);

            WechatUtils.findTextAndClick(this, "返回");

            Thread.sleep(500);

            WechatUtils.findTextAndClick(this, "搜索");

            Thread.sleep(500);

            handleFlow_past();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 搜索界面粘贴要搜索的内容
     */
    private void handleFlow_past() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(BaseLayoutId + searchedittextid);
            if (list != null && list.size() > 0) {
                for (final AccessibilityNodeInfo node : list) {
                    if (node.getClassName().equals("android.widget.EditText") && node.isEnabled()) {
                        try {
                            Thread.sleep(350);

                            WechatUtils.pastContent(this, node, PinYinUtil.getPinYinUtil().getStringPinYin(WechatUtils.NAME));

                            Thread.sleep(500);

                            clickSearchResult();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * 点击搜索到的结果
     */
    private void clickSearchResult() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list1 = nodeInfo.findAccessibilityNodeInfosByViewId(BaseLayoutId + searchlistviewid);
            if (list1 != null && list1.size() > 0) {
                AccessibilityNodeInfo listInfo = list1.get(0);
                for (int i = 0; i < listInfo.getChildCount(); i++) {
                    AccessibilityNodeInfo itemNodeInfo = listInfo.getChild(i);
                    for (int j = 0; j < itemNodeInfo.getChildCount(); j++) {
                        CharSequence name = itemNodeInfo.getChild(j).getText();
                        Log.i(TAG, "childName:" + name);
                        if (!TextUtils.isEmpty(name)
                                && TextUtils.equals(PinYinUtil.getPinYinUtil().getStringPinYin(name.toString()),
                                PinYinUtil.getPinYinUtil().getStringPinYin(WechatUtils.NAME))) {
                            itemNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            return;
                        }
                    }
                }
            }
        }
        WechatUtils.NAME = "";
        WechatUtils.CONTENT = "";
        isSendSuccess = true;
        Log.i(TAG, "没有找到联系人");
        try {
            // 没找到联系人，一定是在搜索页面，这时候要先点一次返回 退出搜索页面，然后在退出微信
            // 防止直接退出微信，下一次发微信直接调起微信显示搜索页面，这时候粘贴内容就跟上一次的内容追加了，结果就不是想要的了
            Thread.sleep(100);
            WechatUtils.findTextAndClick(this, "返回");
            Thread.sleep(200);
            sendBroadcast(new Intent("FIND_CONTANCT_RESULT"));
            resetAndReturnApp();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void handleFlow_ChatUI() {

        //如果微信已经处于聊天界面，需要判断当前联系人是不是需要发送的联系人
        String curUserName = WechatUtils.findTextById(this, BaseLayoutId + chatuiusernameid);
        if (!TextUtils.isEmpty(curUserName)
                && TextUtils.equals(PinYinUtil.getPinYinUtil().getStringPinYin(curUserName),
                PinYinUtil.getPinYinUtil().getStringPinYin(WechatUtils.NAME))) {
            WechatUtils.NAME = "";
            if (TextUtils.isEmpty(WechatUtils.CONTENT)) {
                if (WechatUtils.findViewId(this, BaseLayoutId + chatuiedittextid)) {
                    //当前页面可能处于发送文字状态，需要切换成发送文本状态
                    WechatUtils.findViewIdAndClick(this, BaseLayoutId + chatuiswitchid);
                }
                isSendSuccess = true;
                return;
            }
            if (WechatUtils.findViewByIdAndPasteContent(this, BaseLayoutId + chatuiedittextid, WechatUtils.CONTENT)) {
                sendContent();
            } else {
                //当前页面可能处于发送语音状态，需要切换成发送文本状态
                WechatUtils.findViewIdAndClick(this, BaseLayoutId + chatuiswitchid);

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (WechatUtils.findViewByIdAndPasteContent(this, BaseLayoutId + chatuiedittextid, WechatUtils.CONTENT))
                    sendContent();
            }
        } else {
            //回到主界面
            WechatUtils.findTextAndClick(this, "返回");

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            WechatUtils.findTextAndClick(this, "返回");//再次点击返回，目的是防止上一次返回到搜索页面，那样就阻塞住了
        }
    }

    private void sendContent() {
        //发送成功   能执行这一步，基本上就是发出去了
        WechatUtils.findTextAndClick(this, "发送");
        WechatUtils.NAME = "";
        WechatUtils.CONTENT = "";
        isSendSuccess = true;

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void resetAndReturnApp() {
        isSendSuccess = true;
        ActivityManager activtyManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTaskInfos = activtyManager.getRunningTasks(3);
        for (ActivityManager.RunningTaskInfo runningTaskInfo : runningTaskInfos) {
            if (this.getPackageName().equals(runningTaskInfo.topActivity.getPackageName())) {
                activtyManager.moveTaskToFront(runningTaskInfo.id, ActivityManager.MOVE_TASK_WITH_HOME);
                return;
            }
        }
    }

    /**
     * 拉起微信界面
     *
     * @param event 服务事件
     */
    private void sendNotifacationReply(AccessibilityEvent event) {
        if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
            Notification notification = (Notification) event.getParcelableData();
            String content = notification.tickerText.toString();
            String[] cc = content.split(":");

            String receiveName = cc[0].trim();
            String receciveScontent = cc[1].trim();

            PendingIntent pendingIntent = notification.contentIntent;
            try {
                isSendSuccess = true;
                pendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
            Toast.makeText(this, content, Toast.LENGTH_LONG).show();
        }
    }
}
