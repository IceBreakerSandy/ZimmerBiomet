package sg.totalebizsolutions.genie.views.home;

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import sg.totalebizsolutions.genie.views.categories.CategoryMetaData;

class HomeCategoryGridLayoutManager extends GridLayoutManager
{
  private Context m_context;

  HomeCategoryGridLayoutManager (Context context)
  {
    super(context, 3);
    m_context = context;
  }

  @Override
  public void onMeasure (RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec,
      int heightSpec)
  {
    final float dpi = m_context.getResources().getDisplayMetrics().density;
    final int padding = (int) (10 * dpi);
    final int itemHeight = (int) (24 * dpi);

    int size = CategoryMetaData.SELECTED_CATEGORIES.size();
    int height = (size > 3 ? itemHeight * 2 : itemHeight) + padding * 2;
    heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);

    super.onMeasure(recycler, state, widthSpec, heightSpec);
  }
}