package com.liang.victor.myssrlib;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.github.shadowsocks.Shadowsocks;
import com.github.shadowsocks.utils.SS_SDK;


public class MainActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //switchVpn();
        Button button = (Button) findViewById(R.id.switchButton);
        button.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        SS_SDK.getInstance().setProfile("182.61.100.95",10369,"5X8BAfwLsPmC");
        SS_SDK.getInstance().switchVpn(this);
    }
}
