package com.frank.audiostudy;

import com.frank.audiostudy.slice.AudioRecordSlice;
import com.frank.audiostudy.slice.MainAbilitySlice;
import ohos.aafwk.ability.Ability;
import ohos.aafwk.content.Intent;
import ohos.security.SystemPermission;

public class MainAbility extends Ability {
    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setMainRoute(AudioRecordSlice.class.getName());
        requestPermissionsFromUser(new String[]{SystemPermission.MICROPHONE},1001);
    }

    @Override
    public void onRequestPermissionsFromUserResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsFromUserResult(requestCode, permissions, grantResults);
    }
}
