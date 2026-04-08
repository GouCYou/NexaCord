package cn.cctstudio.nexacord.service;

import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Server;

import java.util.List;

public interface ChannelService {
    Channel createChannel(Channel channel, Server server);
    Channel getChannelById(Long id);
    List<Channel> getChannelsByServer(Server server);
    List<Channel> getChannelsByServerId(Long serverId);
    Channel updateChannel(Channel channel);
    void deleteChannel(Long id);
}