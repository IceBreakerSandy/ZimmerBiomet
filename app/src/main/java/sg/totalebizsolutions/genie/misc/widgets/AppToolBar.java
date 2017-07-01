package sg.totalebizsolutions.genie.misc.widgets;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.widget.TextView;

import sg.totalebizsolutions.genie.R;

public class AppToolBar extends Toolbar
{
  /* Properties */

  public String m_title;

  /* Initializations */

  public AppToolBar (Context context)
  {
    super(context);
    initView(context);
  }

  public AppToolBar (Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    initView(context);
  }

  public AppToolBar (Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    initView(context);
  }

  private void initView (Context context)
  {
  }

  @Override
  public void setTitle (CharSequence title)
  {
    TextView textView = (TextView) findViewById(R.id.title_text_view);
    textView.setText(title);
  }
}
