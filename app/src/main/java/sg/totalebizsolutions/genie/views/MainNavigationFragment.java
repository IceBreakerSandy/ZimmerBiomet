package sg.totalebizsolutions.genie.views;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import sg.totalebizsolutions.foundation.view.navigation.NavigationFragment;
import sg.totalebizsolutions.genie.R;
import sg.totalebizsolutions.genie.databinding.MainNavigationFragmentBinding;
import sg.totalebizsolutions.genie.views.explorer.MainExplorerFragment;
import sg.totalebizsolutions.genie.views.home.HomeFragment;
import sg.totalebizsolutions.genie.views.settings.SettingsFragment;

public class MainNavigationFragment extends NavigationFragment
{
  /* Properties */

  private MainNavigationFragmentBinding m_binding;

  /* Life-cycle methods */

  @Nullable
  @Override
  public View onCreateView (LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState)
  {
    /* Setup view. */
    MainNavigationFragmentBinding binding =
        DataBindingUtil.inflate(inflater, R.layout.main_navigation_fragment, container, false);
    m_binding = binding;

    return binding.getRoot();
  }

  @Override
  public void onViewCreated (View view, Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    setRootFragment(new HomeFragment());
    setupToolbar();
  }

  @Override
  public Toolbar getToolbar ()
  {
    return m_binding.toolbar;
  }

  @Override
  protected void onNavigationChanged (Fragment oldFragment, Fragment newFragment)
  {
    super.onNavigationChanged(oldFragment, newFragment);
    if (!(newFragment instanceof MainExplorerFragment))
    {
      setupToolbar();
    }
  }

  private void setupToolbar ()
  {
    int stackSize = getBackStackSize();

    Toolbar toolbar = getToolbar();
    toolbar.setNavigationIcon(stackSize == 0 ? R.drawable.ic_settings : R.drawable.ic_back);
    toolbar.setNavigationOnClickListener(v ->
    {
      if (getBackStackSize() == 0)
      {
        pushFragmentToNavigation(new SettingsFragment(), null, true);
      }
      else
      {
        popFragmentFromNavigation(true);
      }
    });
  }
}
