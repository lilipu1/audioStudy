package com.frank.audiostudy.slice;

import com.frank.audiostudy.ResourceTable;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Component;
import ohos.agp.render.render3d.Task;
import ohos.app.dispatcher.task.TaskPriority;
import ohos.global.resource.NotExistException;
import ohos.global.resource.RawFileDescriptor;
import ohos.global.resource.Resource;
import ohos.media.audio.AudioRenderer;
import ohos.media.audio.AudioRendererInfo;
import ohos.media.audio.AudioStreamInfo;
import ohos.media.common.Source;
import ohos.media.player.Player;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainAbilitySlice extends AbilitySlice {
    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);
    }

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }
}
