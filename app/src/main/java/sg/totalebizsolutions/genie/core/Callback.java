package sg.totalebizsolutions.genie.core;

public interface Callback<T>
{
  int STATUS_OK = 200;

  void onFinished(int status, String message, T data);
}
