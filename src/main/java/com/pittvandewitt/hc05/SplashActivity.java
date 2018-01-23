package com.pittvandewitt.hc05;

import android.content.Intent;
import android.os.Bundle;

public class SplashActivity extends ControlActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, ControlActivity.class);
        startActivity(intent);
        finish();
    }
}