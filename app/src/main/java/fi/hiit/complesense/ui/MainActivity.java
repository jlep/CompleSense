package fi.hiit.complesense.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import fi.hiit.complesense.R;

/**
 * Created by rocsea0626 on 5.6.2014.
 */
public class MainActivity extends Activity
{

    public static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /**
     public void onRestoreInstanceState(Bundle savedInstanceState)
     {
     Log.i(TAG,"onRestoreInstanceState()");
     // Always call the superclass so it can restore the view hierarchy
     super.onRestoreInstanceState(savedInstanceState);

     String activity2Restore = savedInstanceState.
     getString(Constants.CURRENT_ACTIVITY);

     // Restore previous activity from saved instance
     if(activity2Restore.equals(GroupOwnerService.class.getCanonicalName()))
     {
     Intent intent = new Intent(this,GroupOwnerActivity.class);
     startActivity(intent);
     }
     else if(activity2Restore.equals(GroupClientService.class.getCanonicalName()))
     {
     Intent intent = new Intent(this,GroupClientActivity.class);
     startActivity(intent);
     }
     }
     */

    public void onLaunchAsGroupClient(View view)
    {
        Intent intent = new Intent(this,GroupClientActivity.class);
        startActivity(intent);
    }

    public void onLaunchAsGroupOwner(View view)
    {
        Intent intent = new Intent(this,GroupOwnerActivity.class);
        startActivity(intent);
    }
}
