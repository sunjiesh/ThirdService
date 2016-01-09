package cn.com.sunjiesh.thirdpartdemo.service;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cn.com.sunjiesh.thirdpartdemo.common.ThirdpartyDemoConstants;
import cn.com.sunjiesh.thirdpartdemo.common.WechatEventClickMessageEventkeyEnum;
import cn.com.sunjiesh.thirdpartdemo.dao.RedisWechatMessageDao;
import cn.com.sunjiesh.thirdpartdemo.helper.tuling123.TulingConstants;
import cn.com.sunjiesh.thirdpartdemo.helper.tuling123.TulingHelper;
import cn.com.sunjiesh.thirdpartdemo.model.WechatUser;
import cn.com.sunjiesh.thirdpartdemo.response.tuling.TulingResponse;
import cn.com.sunjiesh.utils.thirdparty.base.HttpService;
import cn.com.sunjiesh.wechat.dao.IWechatAccessTokenDao;
import cn.com.sunjiesh.wechat.entity.message.event.WechatReceiveEventPicCommonMessage;
import cn.com.sunjiesh.wechat.entity.message.event.WechatReceiveEventWeixinMessage;
import cn.com.sunjiesh.wechat.handler.WechatMediaHandler;
import cn.com.sunjiesh.wechat.handler.WechatUserHandler;
import cn.com.sunjiesh.wechat.helper.WechatMessageConvertDocumentHelper;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventClickMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventLocationMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventLocationSelectMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventPicSysphotoMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventScanMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventScancodeCommonMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventSubscribeMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventUnSubscribeMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventViewMessageRequest;
import cn.com.sunjiesh.wechat.model.request.event.WechatEventPicPhotoOrAlbumMessageRequest;
import cn.com.sunjiesh.wechat.model.request.message.WechatNormalImageMessageRequest;
import cn.com.sunjiesh.wechat.model.request.message.WechatNormalLinkMessageRequest;
import cn.com.sunjiesh.wechat.model.request.message.WechatNormalLocationMessageRequest;
import cn.com.sunjiesh.wechat.model.request.message.WechatNormalShortvideoMessageRequest;
import cn.com.sunjiesh.wechat.model.request.message.WechatNormalTextMessageRequest;
import cn.com.sunjiesh.wechat.model.request.message.WechatNormalVideoMessageRequest;
import cn.com.sunjiesh.wechat.model.request.message.WechatNormalVoiceMessageRequest;
import cn.com.sunjiesh.wechat.model.response.media.WechatUploadMediaResponse;
import cn.com.sunjiesh.wechat.model.response.message.WechatReceiveReplayImageMessageResponse;
import cn.com.sunjiesh.wechat.model.response.message.WechatReceiveReplayNewsMessageResponse;
import cn.com.sunjiesh.wechat.model.response.message.WechatReceiveReplayTextMessageResponse;
import cn.com.sunjiesh.wechat.model.response.message.WechatReceiveReplayVoiceMessageResponse;
import cn.com.sunjiesh.wechat.model.user.WechatUserDto;
import cn.com.sunjiesh.wechat.service.AbstractWechatMessageReceiveService;
import cn.com.sunjiesh.wechat.service.IWechatMessageReceiveProcessService;
import cn.com.sunjiesh.xcutils.common.base.ServiceException;

@Service
public class CustomMessageReceiveService extends AbstractWechatMessageReceiveService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CustomMessageReceiveService.class);

	private static final String LAST_IMAGE_MESSAGE_MEDIA_ID = "lastImageMessageMediaId";
    
    private static final String LAST_VOICE_MESSAGE_MEDIA_ID = "lastVoiceMessageMediaId";
    
	private static final String LAST_VIDEO_MESSAGE_MEDIA_ID = "lastVideoMessageMediaId";
    
    @Autowired
    private IWechatMessageReceiveProcessService messageReceiveProcessService;
    
    @Autowired
    private RedisWechatMessageDao redisWechatMessageDao;
    
    @Autowired
    protected IWechatAccessTokenDao wechatAccessTokenDao;
    
    @Autowired
    private ThirdpartyUserService thirdpartyUserService;

    private ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(5);

    @Override
    protected Document messageReceive(Document doc4j) throws ServiceException {
        LOGGER.info("Call CustomMessageReceiveService.messageReceive(Document doc4j)方法");
        return super.messageReceive(doc4j);
    }

    @Override
    protected Document messageReceive(WechatEventLocationSelectMessageRequest wechatMessage) {
        try {
            return messageReceiveProcessService.messageReceive(wechatMessage);
        } catch (ServiceException ex) {
            LOGGER.error(ex.getMessage(),ex);
        }
        return null;
    }

    @Override
    protected Document messageRecive(WechatNormalTextMessageRequest textMessage) throws ServiceException {
    	String responseToUserName=textMessage.getFromUserName();
		String responseFromUserName=textMessage.getToUserName();
		
		LOGGER.debug("receive a WechatReceiveNormalTextMessage request ");

        LOGGER.debug("receive a messageRecive request ");
        String toUserName = textMessage.getFromUserName();
        String fromUserName = textMessage.getToUserName();

        String message = textMessage.getContent();
        final TulingResponse response = new TulingHelper().callTuling(message);
        int tulingCode = response.getCode();
        final String content = response.getUrl();
		switch (tulingCode) {
            case TulingConstants.TULING_RESPONSE_CODE_TEXT:
            	WechatReceiveReplayTextMessageResponse textMessageResponse=new WechatReceiveReplayTextMessageResponse(responseToUserName, responseFromUserName);
        		textMessageResponse.setContent(response.getText());
        		return WechatMessageConvertDocumentHelper.textMessageResponseToDocument(textMessageResponse);
            case TulingConstants.TULING_RESPONSE_CODE_LINK:
                //返回圖片需要處理時間，直接返回NULL值，通過異步進行處理發送消息
                scheduledThreadPool.submit(() -> {
                    try {
                        String url = content;
                        String text = response.getText();
                        //下載圖片並且上傳到微信上，生成圖文消息
                        File tmpFile = new HttpService().getFileResponseFromHttpGetMethod(url);
                        if (tmpFile != null) {
                            String fileName = tmpFile.getName().toLowerCase();
                            String fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
                            if (fileType.contains("jpg") || fileType.contains("jpeg") || fileType.contains("png")) {
                                //僅支持的圖片類型
                                WechatUploadMediaResponse uploadMediaResponse = WechatMediaHandler.uploadMedia(tmpFile, "image", wechatAccessTokenDao.get());
                                String mediaId = uploadMediaResponse.getMediaId();
                                LOGGER.debug("微信臨時圖片素材上傳成功，mediaId=" + mediaId);
                            }

                        }
                    } catch (ServiceException ex) {
                        LOGGER.error("Server Error", ex);
                    }
                });

                return replayTextMessage(responseToUserName, responseFromUserName, content);
           
            default:
                return respError(toUserName, fromUserName);
        }
        
        
		
    }

    @Override
    protected Document messageRecive(WechatNormalImageMessageRequest imageMessage) throws ServiceException{
    	String responseToUserName=imageMessage.getFromUserName();
		String responseFromUserName=imageMessage.getToUserName();
		String mediaId=imageMessage.getMediaId();
		redisWechatMessageDao.save(LAST_IMAGE_MESSAGE_MEDIA_ID, mediaId);
		
		final String content = "图片已经上传，midiaId为="+mediaId;
		return replayTextMessage(responseToUserName, responseFromUserName, content);
    }

    @Override
    protected Document messageRecive(WechatNormalVoiceMessageRequest voiceMessage) {
    	String responseToUserName=voiceMessage.getFromUserName();
		String responseFromUserName=voiceMessage.getToUserName();
		String mediaId=voiceMessage.getMediaId();
		redisWechatMessageDao.save(LAST_VOICE_MESSAGE_MEDIA_ID, mediaId);
		String content = "语音已经上传，midiaId为="+mediaId;
		return replayTextMessage(responseToUserName, responseFromUserName, content);
    }

	

    @Override
    protected Document messageRecive(WechatNormalVideoMessageRequest videoMessage) {
    	String responseToUserName=videoMessage.getFromUserName();
		String responseFromUserName=videoMessage.getToUserName();
		String mediaId=videoMessage.getMediaId();
		redisWechatMessageDao.save(LAST_VIDEO_MESSAGE_MEDIA_ID, mediaId);
		String content = "视频已经上传，midiaId为="+mediaId;
		return replayTextMessage(responseToUserName, responseFromUserName, content);
    }

    @Override
    protected Document messageRecive(WechatNormalShortvideoMessageRequest shortVodeoMessage) {
    	String responseToUserName=shortVodeoMessage.getFromUserName();
		String responseFromUserName=shortVodeoMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
    }

    @Override
    protected Document messageRecive(WechatNormalLocationMessageRequest locationMessage) {
    	String responseToUserName=locationMessage.getFromUserName();
		String responseFromUserName=locationMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
    }

    @Override
    protected Document messageRecive(WechatNormalLinkMessageRequest linkMessage) {
    	String responseToUserName=linkMessage.getFromUserName();
		String responseFromUserName=linkMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
    }

    @Override
    protected Document messageRecive(WechatEventClickMessageRequest clickMessage) {
    	
    	//返回对象
    	Document respDoc=null;
    	
    	final String eventKey=clickMessage.getEventKey();
    	final String responseToUserName = clickMessage.getFromUserName();
		final String responseFromUserName = clickMessage.getToUserName();
    	LOGGER.debug("EventKey="+eventKey);
    	WechatEventClickMessageEventkeyEnum eventKeyEnum=WechatEventClickMessageEventkeyEnum.valueOf(eventKey);
    	
		switch(eventKeyEnum){
    	case GetTextMessage:{
    		WechatReceiveReplayTextMessageResponse textMessageResponse=new WechatReceiveReplayTextMessageResponse(responseToUserName, responseFromUserName);
    		textMessageResponse.setContent("Hello,This is a test text message.\n你好！這是一條測試文本消息");
    		respDoc=WechatMessageConvertDocumentHelper.textMessageResponseToDocument(textMessageResponse);
    	};break;
    	case GetImageMessage:{
    		String mediaId=redisWechatMessageDao.get(LAST_IMAGE_MESSAGE_MEDIA_ID);
    		if(StringUtils.isEmpty(mediaId)){
    			final String errorMsg = "没有找到用户上传的图片，请上传一张图片之后再试";
				LOGGER.warn(errorMsg);
    			WechatReceiveReplayTextMessageResponse textMessageResponse=new WechatReceiveReplayTextMessageResponse(responseToUserName, responseFromUserName);
        		textMessageResponse.setContent(errorMsg);
        		respDoc=WechatMessageConvertDocumentHelper.textMessageResponseToDocument(textMessageResponse);
    		}else{
    			WechatReceiveReplayImageMessageResponse imageMessageResponse=new WechatReceiveReplayImageMessageResponse(responseToUserName, responseFromUserName);
        		imageMessageResponse.setMediaId(mediaId);
        		respDoc=WechatMessageConvertDocumentHelper.imageMessageResponseToDocumnet(imageMessageResponse);
    		}
    		
    	};break;
    	case GetVoiceMessage:{
    		String mediaId=redisWechatMessageDao.get(LAST_VOICE_MESSAGE_MEDIA_ID);
    		if(StringUtils.isEmpty(mediaId)){
    			final String errorMsg = "没有找到用户上传的语音，请重新发送一条语音消息之后再试";
				LOGGER.warn(errorMsg);
    			WechatReceiveReplayTextMessageResponse textMessageResponse=new WechatReceiveReplayTextMessageResponse(responseToUserName, responseFromUserName);
        		textMessageResponse.setContent(errorMsg);
        		respDoc=WechatMessageConvertDocumentHelper.textMessageResponseToDocument(textMessageResponse);
    		}else{
    			WechatReceiveReplayVoiceMessageResponse voiceMessageResponse=new WechatReceiveReplayVoiceMessageResponse(responseToUserName, responseFromUserName);
    			voiceMessageResponse.setMediaId(mediaId);
    			respDoc=WechatMessageConvertDocumentHelper.voiceMessageResponseToDocumnet(voiceMessageResponse);
    		}
    	};break;
    	case GETNEWSMESSAGE1:{
    		WechatReceiveReplayNewsMessageResponse newsReplayMessage = new WechatReceiveReplayNewsMessageResponse(responseToUserName, responseFromUserName);
            WechatReceiveReplayNewsMessageResponse.WechatReceiveReplayNewsMessageResponseItem newsReplayMessageItem = newsReplayMessage.new WechatReceiveReplayNewsMessageResponseItem();
            newsReplayMessageItem.setDescription("测试图文消息");
            newsReplayMessageItem.setTitle("测试图片消息");
            newsReplayMessageItem.setUrl("http://ubuntu-sunjiesh.myalauda.cn/index.html");
            newsReplayMessageItem.setPicUrl("http://ubuntu-sunjiesh.myalauda.cn/360_200.jpg");
            newsReplayMessage.addItem(newsReplayMessageItem);
            respDoc=WechatMessageConvertDocumentHelper.newsMessageResponseToDocument(newsReplayMessage);
    	};break;
    	case GETNEWSMESSAGE2:{
    		WechatReceiveReplayNewsMessageResponse newsReplayMessage = new WechatReceiveReplayNewsMessageResponse(responseToUserName, responseFromUserName);
            WechatReceiveReplayNewsMessageResponse.WechatReceiveReplayNewsMessageResponseItem newsReplayMessageItem1 = newsReplayMessage.new WechatReceiveReplayNewsMessageResponseItem();
            newsReplayMessageItem1.setDescription("测试图文消息1");
            newsReplayMessageItem1.setTitle("测试图片消息1");
            newsReplayMessageItem1.setUrl("http://ubuntu-sunjiesh.myalauda.cn/index.html");
            newsReplayMessageItem1.setPicUrl("http://ubuntu-sunjiesh.myalauda.cn/360_200.jpg");
            newsReplayMessage.addItem(newsReplayMessageItem1);
            WechatReceiveReplayNewsMessageResponse.WechatReceiveReplayNewsMessageResponseItem newsReplayMessageItem2 = newsReplayMessage.new WechatReceiveReplayNewsMessageResponseItem();
            newsReplayMessageItem2.setDescription("测试图文消息2");
            newsReplayMessageItem2.setTitle("测试图片消息2");
            newsReplayMessageItem2.setUrl("http://ubuntu-sunjiesh.myalauda.cn/index.html");
            newsReplayMessageItem2.setPicUrl("http://ubuntu-sunjiesh.myalauda.cn/360_200.jpg");
            newsReplayMessage.addItem(newsReplayMessageItem2);
            respDoc=WechatMessageConvertDocumentHelper.newsMessageResponseToDocument(newsReplayMessage);
    	};break;
    	default:{
    		respDoc=respError(responseToUserName, responseFromUserName);
    	}
    	}
    	
		return respDoc;

    }

    @Override
    protected Document messageRecive(WechatEventLocationMessageRequest locationMessage) {
    	String responseToUserName=locationMessage.getFromUserName();
		String responseFromUserName=locationMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
		
    }


    @Override
    protected Document messageRecive(WechatEventScanMessageRequest scanMessage) {
    	String responseToUserName=scanMessage.getFromUserName();
		String responseFromUserName=scanMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
    }

    @Override
    protected Document messageRecive(WechatEventSubscribeMessageRequest subscribeMessage) throws ServiceException {
    	//根据OpenId查询对应的信息
        String wechatOpenId = subscribeMessage.getFromUserName();
        WechatUserDto wechatUserDto = new WechatUserDto();
        wechatUserDto.setOpenId(wechatOpenId);
        wechatUserDto = WechatUserHandler.getUserInfo(wechatUserDto, wechatAccessTokenDao.get());

        //封装WechatUser对象，插入數據到客戶端
        WechatUser wechatUser = new WechatUser();
        try {
            BeanUtils.copyProperties(wechatUser, wechatUserDto);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Convert WechatUserDto To WechatUser Error", e);
        }
        wechatUser.setCreateTime(new Date());
        wechatUser.setCreateUser(ThirdpartyDemoConstants.CREATE_USER_THIRDPARTYDEMO_WEB);
        wechatUser.setUpdateTime(new Date());
        wechatUser.setUpdateUser(ThirdpartyDemoConstants.CREATE_USER_THIRDPARTYDEMO_WEB);
        thirdpartyUserService.save(wechatUser);
        
        String responseToUserName=subscribeMessage.getFromUserName();
		String responseFromUserName=subscribeMessage.getToUserName();
        WechatReceiveReplayTextMessageResponse textMessageResponse=new WechatReceiveReplayTextMessageResponse(responseToUserName, responseFromUserName);
		textMessageResponse.setContent("谢谢关注公众号");
		return WechatMessageConvertDocumentHelper.textMessageResponseToDocument(textMessageResponse);
    }
    
    @Override
	protected Document messageRecive(WechatEventUnSubscribeMessageRequest unSubscribeMessage) throws ServiceException {
    	//根据OpenId查询对应的信息
        String wechatOpenId = unSubscribeMessage.getFromUserName();
        WechatUserDto wechatUserDto = new WechatUserDto();
        wechatUserDto.setOpenId(wechatOpenId);
        wechatUserDto = WechatUserHandler.getUserInfo(wechatUserDto, wechatAccessTokenDao.get());
        
        //封装WechatUser对象，插入數據到客戶端
        WechatUser wechatUser = new WechatUser();
        try {
            BeanUtils.copyProperties(wechatUser, wechatUserDto);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Convert WechatUserDto To WechatUser Error", e);
        }
        thirdpartyUserService.delete(wechatUser);
        
        String responseToUserName=unSubscribeMessage.getFromUserName();
		String responseFromUserName=unSubscribeMessage.getToUserName();
        WechatReceiveReplayTextMessageResponse textMessageResponse=new WechatReceiveReplayTextMessageResponse(responseToUserName, responseFromUserName);
		textMessageResponse.setContent("希望下次再次关注公众号");
		return WechatMessageConvertDocumentHelper.textMessageResponseToDocument(textMessageResponse);
	}

    @Override
    protected Document messageRecive(WechatEventViewMessageRequest viewMessage) {
    	String responseToUserName=viewMessage.getFromUserName();
		String responseFromUserName=viewMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
    }

    @Override
    protected Document messageRecive(WechatEventScancodeCommonMessageRequest scanCodePushMessage) {
    	String responseToUserName=scanCodePushMessage.getFromUserName();
		String responseFromUserName=scanCodePushMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
    }


    @Override
    protected Document messageRecive(WechatEventPicSysphotoMessageRequest picSysphotoMessage) {
    	String responseToUserName=picSysphotoMessage.getFromUserName();
		String responseFromUserName=picSysphotoMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
    }

    @Override
    protected Document messageRecive(WechatEventPicPhotoOrAlbumMessageRequest picPhotoOrAlbumEventMessage) {
    	String responseToUserName=picPhotoOrAlbumEventMessage.getFromUserName();
		String responseFromUserName=picPhotoOrAlbumEventMessage.getToUserName();
		return respError(responseToUserName, responseFromUserName);
    }

    /**
     * 错误消息返回
     * @param responseToUserName
     * @param responseFromUserName
     * @return
     */
    protected Document respError(String responseToUserName, String responseFromUserName) {
		WechatReceiveReplayTextMessageResponse textMessageResponse=new WechatReceiveReplayTextMessageResponse(responseToUserName, responseFromUserName);
		textMessageResponse.setContent("暂不支持");
		return WechatMessageConvertDocumentHelper.textMessageResponseToDocument(textMessageResponse);
	}

	
}
