package cl.coders.faketraveler;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.widget.EditText;
import android.widget.TextView;

public class MoreActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);
        EditText editText2;
        EditText editText3;
        EditText editText4;
        TextView textView3;
        Context context;
        SharedPreferences sharedPref;

        context = getApplicationContext();
        sharedPref = context.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);

        textView3 = findViewById(R.id.textView3);
        textView3.setMovementMethod(LinkMovementMethod.getInstance());

        editText2 = findViewById(R.id.editText2);
        editText3 = findViewById(R.id.editText3);
        editText4 = findViewById(R.id.editText4);
        editText2.setText(sharedPref.getString("howManyTimes", "0"));
        editText3.setText(sharedPref.getString("timeInterval", "10"));
        editText4.setText(sharedPref.getString("speedLimit", "40"));


        editText2.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                EditText editText2 = findViewById(R.id.editText2);
                Context context = getApplicationContext();
                SharedPreferences sharedPref = context.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();

                if (editText2.getText().toString().isEmpty()) {
                    editor.putString("howManyTimes", "0");
                    MainActivity.howManyTimes = 1;
                } else {
                    editor.putString("howManyTimes", editText2.getText().toString());
                    MainActivity.howManyTimes = Integer.parseInt(editText2.getText().toString());
                }

                editor.commit();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        editText3.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {

                EditText editText3 = findViewById(R.id.editText3);
                Context context = getApplicationContext();
                SharedPreferences mSharedPref = context.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = mSharedPref.edit();

                if (editText3.getText().toString().isEmpty()) {
                    editor.putString("timeInterval", "1000");
                    MainActivity.timeInterval = 1000;
                } else {
                    editor.putString("timeInterval", editText3.getText().toString());
                    int value = Integer.parseInt(editText3.getText().toString());
                    if (value>=100) {
                        MainActivity.timeInterval = value;
                    }
                }

                editor.commit();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
        });

        editText4.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                EditText editText4 = findViewById(R.id.editText4);
                Context context = getApplicationContext();
                SharedPreferences mSharedPref = context.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = mSharedPref.edit();

                if (editText4.getText().toString().isEmpty()) {
                    editor.putString("speedLimit", "40");
                    MainActivity.speedLimit = 40;
                } else {
                    editor.putString("speedLimit", editText4.getText().toString());
                    MainActivity.speedLimit = Integer.parseInt(editText4.getText().toString());
                }
                editor.commit();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }
}
