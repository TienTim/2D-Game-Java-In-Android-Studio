package com.mobileclass.flywithme;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.mobileclass.flywithme.utils.OpenClass;

public class Store extends AppCompatActivity {
    OpenClass data = new OpenClass();
    ImageView back, health, rocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake_animation);
        health = findViewById(R.id.healthImage);
        rocket = findViewById(R.id.rocketImage);
        back = findViewById(R.id.btnBack);

        back.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                String place = data.get_place();
                if (place == "theme")
                    startActivity(new Intent(Store.this, ChooseTheme.class));
                else
                    startActivity(new Intent(Store.this, ChooseCharacter.class));
                return false;
            }
        });

        health.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(data.getHealthAmount() <=2) {
                    health.startAnimation(shake);
                    data.addHealth(1);
                    if(data.getHealthAmount() == 1){
                        health.setImageResource(R.drawable.heathheath1);
                    }
                    else if(data.getHealthAmount() == 2)
                    {
                        health.setImageResource(R.drawable.heathheath2);
                    }
                    else if(data.getHealthAmount() == 3)
                    {
                        health.setImageResource(R.drawable.heathheath3);
                    }
                }
                return false;
            }
        });

        rocket.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(data.getRocketAmount() == 0) {
                    rocket.startAnimation(shake);
                    rocket.setImageResource(R.drawable.rocket_1);
                    data.addRocket(1);
                }
                return false;
            }
        });
    }
}