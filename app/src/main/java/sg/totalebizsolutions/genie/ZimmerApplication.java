package sg.totalebizsolutions.genie;

import android.app.Application;

import io.realm.Realm;
import sg.totalebizsolutions.genie.core.ZimmerServices;

public class ZimmerApplication extends Application
{
  /* Life-cycle methods */

  @Override
  public void onCreate ()
  {
    super.onCreate();
    Realm.init(this);
    ZimmerServices.init(this);
    ZimmerServices.getInstance().onCreate();
  }

  @Override
  public void onLowMemory ()
  {
    super.onLowMemory();
    ZimmerServices.getInstance().onLowMemory();
  }
}