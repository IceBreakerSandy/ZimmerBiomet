package sg.totalebizsolutions.genie.views.explorer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewAnimator;

import com.artifex.mupdfdemo.Annotation;
import com.artifex.mupdfdemo.AsyncTask;
import com.artifex.mupdfdemo.ChoosePDFActivity;
import com.artifex.mupdfdemo.FilePicker;
import com.artifex.mupdfdemo.Hit;
import com.artifex.mupdfdemo.MuPDFAlert;
import com.artifex.mupdfdemo.MuPDFCore;
import com.artifex.mupdfdemo.MuPDFPageAdapter;
import com.artifex.mupdfdemo.MuPDFReaderView;
import com.artifex.mupdfdemo.MuPDFReflowAdapter;
import com.artifex.mupdfdemo.MuPDFView;
import com.artifex.mupdfdemo.OutlineActivity;
import com.artifex.mupdfdemo.OutlineActivityData;
import com.artifex.mupdfdemo.OutlineItem;
import com.artifex.mupdfdemo.PrintDialogActivity;
import com.artifex.mupdfdemo.ReaderView;
import com.artifex.mupdfdemo.SafeAnimatorInflater;
import com.artifex.mupdfdemo.SearchTask;
import com.artifex.mupdfdemo.SearchTaskResult;
import com.artifex.mupdfdemo.ThreadPerTaskExecutor;
import com.artifex.utils.DigitalizedEventCallback;
import com.artifex.utils.PdfBitmap;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import sg.totalebizsolutions.foundation.view.navigation.NavigationItemFragment;
import sg.totalebizsolutions.genie.R;
import sg.totalebizsolutions.genie.core.file.File;
import sg.totalebizsolutions.genie.util.Logger;

public class MuPDFFragment extends NavigationItemFragment implements FilePicker.FilePickerSupport {
    private static final String TAG = "MuPDFFragment";
    public static final String PARAM_SIGN_BITMAP_PATH = "paramSignBitmapPath";
    public static final String PARAM_DIGITALIZED_IMAGE = "paramDigitalizedImage";
    public static final String PARAM_PATH_PDF = "paramPathPdf";
    public static final String PARAM_SHOW_CONTROLS = "paramShowControls";
    public static final String PARAM_MODE_SIGN = "doSign";
    private static final String BUNDLE_FILE = "file";
    /* State restoration */
    private static final String BUNDLE_FILENAME = "savedFileName";
    private static final String BUNDLE_BUTTONS_HIDDEN = "savedButtonsHidden";
    private File m_file;
    private RelativeLayout layout;
    private View rootView;
    /*private TextView fileNameTextView;
    private TextView breadcrumbsTextView;
    private TextView formatTextView;*/
    private long m_fileID;
    private static final String BUNDLE_FILE_ID = "fileID";
    private static final String BUNDLE_TITLE = "title";
    private static final String AUTHORITY = "sg.totalebizsolutions.zimmer";

    /* The core rendering instance */
    enum TopBarMode {
        Main, Search, Annot, Delete, More, Accept
    }

    ;

    enum AcceptMode {Highlight, Underline, StrikeOut, Ink, CopyText}

    ;

    private Context mContext;
    private final int OUTLINE_REQUEST = 0;
    private final int PRINT_REQUEST = 1;
    private final int FILEPICK_REQUEST = 2;
    private MuPDFCore core;
    private String mFileName;
    private MuPDFReaderView mDocView;
    private View mButtonsView;
    private boolean mButtonsVisible;
    private EditText mPasswordView;
    private TextView mFilenameView;
    private SeekBar mPageSlider;
    private int mPageSliderRes;
    private TextView mPageNumberView;
    private TextView mInfoView;
    private ImageButton mSearchButton;
    private ImageButton mReflowButton;
    private ImageButton mOutlineButton;
    private ImageButton mMoreButton;
    private TextView mAnnotTypeText;
    private ImageButton mAnnotButton;
    private ViewAnimator mTopBarSwitcher;
    private ImageButton mLinkButton;
    private TopBarMode mTopBarMode = TopBarMode.Main;
    private AcceptMode mAcceptMode;
    private ImageButton mSearchBack;
    private ImageButton mSearchFwd;
    private EditText mSearchText;
    private SearchTask mSearchTask;
    private AlertDialog.Builder mAlertBuilder;
    private boolean mDoSign;
    private DigitalizedEventCallback eventCallback;
    private boolean mLinkHighlight = false;
    private final Handler mHandler = new Handler();
    private boolean mAlertsActive = false;
    private boolean mReflow = false;
    private AsyncTask<Void, Void, MuPDFAlert> mAlertTask;
    private AlertDialog mAlertDialog;
    private FilePicker mFilePicker;
    private Collection<PdfBitmap> pdfBitmaps;
    private byte[] byteArrayPdf;
    private int mPageNumber = 0;
    private String m_title;

    public void createAlertWaiter() {
        mAlertsActive = true;
        // All mupdf library calls are performed on asynchronous tasks to avoid stalling
        // the UI. Some calls can lead to javascript-invoked requests to display an
        // alert dialog and collect a reply from the user. The task has to be blocked
        // until the user's reply is received. This method creates an asynchronous task,
        // the purpose of which is to wait of these requests and produce the dialog
        // in response, while leaving the core blocked. When the dialog receives the
        // user's response, it is sent to the core via replyToAlert, unblocking it.
        // Another alert-waiting task is then created to pick up the next alert.
        if (mAlertTask != null) {
            mAlertTask.cancel(true);
            mAlertTask = null;
        }
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }
        mAlertTask = new AsyncTask<Void, Void, MuPDFAlert>() {

            @Override
            protected MuPDFAlert doInBackground(Void... arg0) {
                if (!mAlertsActive)
                    return null;

                return core.waitForAlert();
            }

            @Override
            protected void onPostExecute(final MuPDFAlert result) {
                // core.waitForAlert may return null when shutting down
                if (result == null)
                    return;
                final MuPDFAlert.ButtonPressed pressed[] = new MuPDFAlert.ButtonPressed[3];
                for (int i = 0; i < 3; i++)
                    pressed[i] = MuPDFAlert.ButtonPressed.None;
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertDialog = null;
                        if (mAlertsActive) {
                            int index = 0;
                            switch (which) {
                                case AlertDialog.BUTTON1:
                                    index = 0;
                                    break;
                                case AlertDialog.BUTTON2:
                                    index = 1;
                                    break;
                                case AlertDialog.BUTTON3:
                                    index = 2;
                                    break;
                            }
                            result.buttonPressed = pressed[index];
                            // Send the user's response to the core, so that it can
                            // continue processing.
                            core.replyToAlert(result);
                            // Create another alert-waiter to pick up the next alert.
                            createAlertWaiter();
                        }
                    }
                };
                mAlertDialog = mAlertBuilder.create();
                mAlertDialog.setTitle(result.title);
                mAlertDialog.setMessage(result.message);
                switch (result.iconType) {
                    case Error:
                        break;
                    case Warning:
                        break;
                    case Question:
                        break;
                    case Status:
                        break;
                }
                switch (result.buttonGroupType) {
                    case OkCancel:
                        mAlertDialog.setButton(AlertDialog.BUTTON2, getString(com.artifex.mupdfdemo.R.string.cancel), listener);
                        pressed[1] = MuPDFAlert.ButtonPressed.Cancel;
                    case Ok:
                        mAlertDialog.setButton(AlertDialog.BUTTON1, getString(com.artifex.mupdfdemo.R.string.okay), listener);
                        pressed[0] = MuPDFAlert.ButtonPressed.Ok;
                        break;
                    case YesNoCancel:
                        mAlertDialog.setButton(AlertDialog.BUTTON3, getString(com.artifex.mupdfdemo.R.string.cancel), listener);
                        pressed[2] = MuPDFAlert.ButtonPressed.Cancel;
                    case YesNo:
                        mAlertDialog.setButton(AlertDialog.BUTTON1, getString(com.artifex.mupdfdemo.R.string.yes), listener);
                        pressed[0] = MuPDFAlert.ButtonPressed.Yes;
                        mAlertDialog.setButton(AlertDialog.BUTTON2, getString(com.artifex.mupdfdemo.R.string.no), listener);
                        pressed[1] = MuPDFAlert.ButtonPressed.No;
                        break;
                }
                mAlertDialog.setOnCancelListener(new OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        mAlertDialog = null;
                        if (mAlertsActive) {
                            result.buttonPressed = MuPDFAlert.ButtonPressed.None;
                            core.replyToAlert(result);
                            createAlertWaiter();
                        }
                    }
                });

                mAlertDialog.show();
            }
        };

        mAlertTask.executeOnExecutor(new ThreadPerTaskExecutor());
    }

    public void destroyAlertWaiter() {
        mAlertsActive = false;
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }
        if (mAlertTask != null) {
            mAlertTask.cancel(true);
            mAlertTask = null;
        }
    }

    private MuPDFCore openFile(String path) {
        int lastSlashPos = path.lastIndexOf('/');
        mFileName = new String(lastSlashPos == -1
                ? path
                : path.substring(lastSlashPos + 1));
        System.out.println("Trying to open " + path);
        try {
            core = new MuPDFCore(mContext, path);
            // New file: drop the old outline data
            OutlineActivityData.set(null);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
        return core;
    }


    private MuPDFCore openBuffer(byte buffer[], String magic) {
        System.out.println("Trying to open byte buffer");
        try {
            core = new MuPDFCore(mContext, buffer, magic);
            // New file: drop the old outline data
            OutlineActivityData.set(null);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
        return core;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        m_file = args.getParcelable(BUNDLE_FILE);

        String s = m_file.getFormat();
        Logger.debug("file type" + s);

        if (savedInstanceState != null) {
            m_file = savedInstanceState.getParcelable(BUNDLE_FILE);
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = MuPDFFragment.this.getActivity();
        //Fuerza la orientacion a landscape
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // rootView = View.inflate(mContext,R.layout.fragment_mupdf, null);

        rootView = getActivity().getLayoutInflater().inflate(R.layout.fragment_mupdf, null);
        mAlertBuilder = new AlertDialog.Builder(mContext);
        coreObjectSetup(savedInstanceState);
        setUpToolBar();
        initUI();
        mDocView = createUI(savedInstanceState, mContext, layout);
        setTextValues(mDocView);
        return rootView;
    }

    private void setTextValues(MuPDFReaderView mDocView) {

        // fileNameTextView.setText(m_file.getDisplayName());
        // formatTextView.setText(m_file.getFileName());
        StringBuilder builder = new StringBuilder();
        buildBreadcrumbs(m_file, builder);
        //  breadcrumbsTextView.setText(builder.toString());
        RelativeLayout rl = (RelativeLayout) rootView.findViewById(R.id.mupdf_wrapper);
        rl.removeAllViews();

        layout.addView(mDocView);
    }

    private void initUI() {

        layout = (RelativeLayout) rootView.findViewById(R.id.mupdf_wrapper);
      /*  fileNameTextView = (TextView) rootView.findViewById(R.id.file_name_text_view);
        breadcrumbsTextView = (TextView) rootView.findViewById(R.id.breadcrumbs_text_view);
        formatTextView = (TextView) rootView.findViewById(R.id.format_text_view);*/
        //RelativeLayout layout = new RelativeLayout(context);
    }

    private void setUpToolBar() {
        Toolbar toolbar = getToolbar();
        toolbar.inflateMenu(R.menu.share);
        toolbar.setTitle(m_file.getFileName());

        toolbar.setOnMenuItemClickListener(menuItem ->
        {

            shareDoc();

            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        hideKeyBoard();
    }

    private void shareDoc() {
        Intent intent = getActivity().getIntent();
        byte buffer[] = null;

        boolean hasIntent = Intent.ACTION_VIEW.equals(intent.getAction());
        boolean hasArguments = getArguments() != null && getArguments().getString(PARAM_PATH_PDF) != null;

        if (hasIntent || hasArguments) {
            Uri uri;
            if (hasArguments) {
                uri = Uri.parse(getArguments().getString(PARAM_PATH_PDF));
                java.io.File fileIs = new java.io.File(uri.getPath());
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                Uri fileUri = FileProvider.getUriForFile(mContext, AUTHORITY, fileIs);
                emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // Intent emailIntent = new Intent(Intent.ACTION_SEND);
                // set the type to 'email'
                emailIntent.setType("vnd.android.cursor.dir/email");
                // String to[] = {"sandeep.devhare@apar.com"};
                // emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
                // the attachment
                emailIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                // the mail subject
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Share from Zimmer mobile app.");
                emailIntent.putExtra(Intent.EXTRA_TEXT, "I am sharing you " + m_file.getDisplayName() + "." + m_file.getFormat() + " file.");
                //startActivity(Intent.createChooser(emailIntent, "Send email..."));
                startActivityForResult(Intent.createChooser(emailIntent, "Send email..."), 0);
            }
        }


    }

    private void coreObjectSetup(Bundle savedInstanceState) {
        try {

            if (core == null) {
                core = (MuPDFCore) getActivity().getLastNonConfigurationInstance();

                if (savedInstanceState != null && savedInstanceState.containsKey("FileName")) {
                    mFileName = savedInstanceState.getString("FileName");
                }
                if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_FILE)) {
                    Bundle args = getArguments();
                    m_file = args.getParcelable(BUNDLE_FILE);
                    String s = m_file.getFormat();
                    Logger.debug("file type" + s);
                }
            }
            if (core == null) {
                Intent intent = getActivity().getIntent();
                byte buffer[] = null;

                boolean hasIntent = Intent.ACTION_VIEW.equals(intent.getAction());
                boolean hasArguments = getArguments() != null && getArguments().getString(PARAM_PATH_PDF) != null;

                if (hasIntent || hasArguments) {
                    Uri uri;
                    if (hasArguments) {
                        uri = Uri.parse(getArguments().getString(PARAM_PATH_PDF));
                    } else {
                        uri = intent.getData();
                        mDoSign = intent.getBooleanExtra(PARAM_MODE_SIGN, true);
                    }
                    if ((uri != null && uri.toString().startsWith("content://")) || byteArrayPdf != null) {
                        String reason = null;
                        try {
                            if (byteArrayPdf != null) {
                                buffer = byteArrayPdf;
                            } else {
                                InputStream is = mContext.getContentResolver().openInputStream(uri);
                                int len = is.available();
                                buffer = new byte[len];
                                is.read(buffer, 0, len);
                                is.close();
                            }
                        } catch (OutOfMemoryError e) {
                            System.out.println("Out of memory during buffer reading");
                            reason = e.toString();
                        } catch (Exception e) {
                            System.out.println("Exception reading from stream: " + e);

                            // Handle view requests from the Transformer Prime's file manager
                            // Hopefully other file managers will use this same scheme, if not
                            // using explicit paths.
                            // I'm hoping that this case below is no longer needed...but it's
                            // hard to test as the file manager seems to have changed in 4.x.
                            try {
                                Cursor cursor = mContext.getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
                                if (cursor.moveToFirst()) {
                                    String str = cursor.getString(0);
                                    if (str == null) {
                                        reason = "Couldn't parse data in intent";
                                    } else {
                                        uri = Uri.parse(str);
                                    }
                                }
                            } catch (Exception e2) {
                                System.out.println("Exception in Transformer Prime file manager code: " + e2);
                                reason = e2.toString();
                            }
                        }
                        if (reason != null) {
                            buffer = null;
                            Resources res = getResources();
                            AlertDialog alert = mAlertBuilder.create();
                            alert.setTitle(String.format(res.getString(com.artifex.mupdfdemo.R.string.cannot_open_document_Reason), reason));
                            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(com.artifex.mupdfdemo.R.string.dismiss),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            getActivity().finish();
                                        }
                                    });
                            alert.show();
                            //return null;
                        }
                    }
                    if (buffer != null) {
                        core = openBuffer(buffer, intent.getType());
                    } else {
                        core = openFile(Uri.decode(uri.getEncodedPath()));
                    }
                    SearchTaskResult.set(null);
                }
                if (core != null && core.needsPassword()) {
                    requestPassword(savedInstanceState);
                    //return null;
                }
                if (core != null && core.countPages() == 0) {
                    core = null;
                }
            }
            if (core == null) {
                AlertDialog alert = mAlertBuilder.create();
                alert.setTitle(com.artifex.mupdfdemo.R.string.cannot_open_document);
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(com.artifex.mupdfdemo.R.string.dismiss),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                getActivity().finish();
                            }
                        });
                alert.setOnCancelListener(new OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        getActivity().finish();
                    }
                });
                alert.show();
                //return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void requestPassword(final Bundle savedInstanceState) {
        mPasswordView = new EditText(mContext);
        mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        mPasswordView.setTransformationMethod(new PasswordTransformationMethod());

        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(com.artifex.mupdfdemo.R.string.enter_password);
        alert.setView(mPasswordView);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(com.artifex.mupdfdemo.R.string.okay),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (core.authenticatePassword(mPasswordView.getText().toString())) {
                            createUI(savedInstanceState, mContext, layout);
                        } else {
                            requestPassword(savedInstanceState);
                        }
                    }
                });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(com.artifex.mupdfdemo.R.string.cancel),
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        getActivity().finish();
                    }
                });
        alert.show();
    }

    public MuPDFReaderView createUI(Bundle savedInstanceState, final Context context, RelativeLayout layout) {
        if (core == null)
            return null;

        // Now create the UI.
        // First create the document view
        mDocView = new MuPDFReaderView(context) {
            @Override
            protected void onMoveToChild(int i) {
                if (core == null)
                    return;
                mPageNumberView.setText(String.format("%d / %d", i + 1,
                        core.countPages()));
                mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
                mPageSlider.setProgress(i * mPageSliderRes);

                super.onMoveToChild(i);
            }

            @Override
            protected void onTapMainDocArea() {
                if (!mButtonsVisible) {
                    showButtons();
                } else {
                    if (mTopBarMode == TopBarMode.Main)
                        hideButtons();
                }
            }

            @Override
            protected void onDocMotion() {
                hideButtons();
            }

            @Override
            protected void onHit(Hit item) {
                switch (mTopBarMode) {
                    case Annot:
                        if (item == Hit.Annotation) {
                            showButtons();
                            mTopBarMode = TopBarMode.Delete;
                            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
                        }
                        break;
                    case Delete:
                        mTopBarMode = TopBarMode.Annot;
                        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
                        // fall through
                    default:
                        // Not in annotation editing mode, but the pageview will
                        // still select and highlight hit annotations, so
                        // deselect just in case.
                        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
                        if (pageView != null)
                            pageView.deselectAnnotation();
                        break;
                }
            }
        };
        MuPDFPageAdapter adapter = new MuPDFPageAdapter(context, this, core);
        mDocView.setAdapter(adapter);

        mDocView.setEventCallback(eventCallback);
        mDocView.setPdfBitmapList(pdfBitmaps);

        mSearchTask = new SearchTask(context, core) {
            @Override
            protected void onTextFound(SearchTaskResult result) {
                SearchTaskResult.set(result);
                // Ask the ReaderView to move to the resulting page
                mDocView.setDisplayedViewIndex(result.pageNumber);
                // Make the ReaderView act on the change to SearchTaskResult
                // via overridden onChildSetup method.
                mDocView.resetupChildren();
            }
        };

        // Make the buttons overlay, and store all its
        // controls in variables
        makeButtonsView();

        // Set up the page slider
        int smax = Math.max(core.countPages() - 1, 1);
        mPageSliderRes = ((10 + smax - 1) / smax) * 2;

        // Set the file-name text
        mFilenameView.setText("");

        // Activate the seekbar
        mPageSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
                mDocView.setDisplayedViewIndex((seekBar.getProgress() + mPageSliderRes / 2) / mPageSliderRes);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                updatePageNumView((progress + mPageSliderRes / 2) / mPageSliderRes);
            }
        });

        // Activate the search-preparing button
        mSearchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                searchModeOn();
            }
        });

        // Activate the reflow button
        mReflowButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleReflow();
            }
        });

        if (core.fileFormat().startsWith("PDF") && core.isUnencryptedPDF() && !core.wasOpenedFromBuffer()) {
            mAnnotButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mTopBarMode = TopBarMode.Annot;
                    mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
                }
            });
        } else {
            mAnnotButton.setVisibility(View.GONE);
        }

        // Search invoking buttons are disabled while there is no text specified
        mSearchBack.setEnabled(false);
        mSearchFwd.setEnabled(false);
        mSearchBack.setColorFilter(Color.argb(255, 128, 128, 128));
        mSearchFwd.setColorFilter(Color.argb(255, 128, 128, 128));

        // React to interaction with the text widget
        mSearchText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                boolean haveText = s.toString().length() > 0;
                setButtonEnabled(mSearchBack, haveText);
                setButtonEnabled(mSearchFwd, haveText);

                // Remove any previous search results
                if (SearchTaskResult.get() != null && !mSearchText.getText().toString().equals(SearchTaskResult.get().txt)) {
                    SearchTaskResult.set(null);
                    mDocView.resetupChildren();
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }
        });

        //React to Done button on keyboard
        mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE)
                    search(1);
                return false;
            }
        });

        mSearchText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER)
                    search(1);
                return false;
            }
        });

        // Activate search invoking buttons
        mSearchBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                search(-1);
            }
        });
        mSearchFwd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                search(1);
            }
        });

        mLinkButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setLinkHighlight(!mLinkHighlight);
            }
        });

        if (core.hasOutline()) {
            mOutlineButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    OutlineItem outline[] = core.getOutline();
                    if (outline != null) {
                        OutlineActivityData.get().items = outline;
                        Intent intent = new Intent(context, OutlineActivity.class);
                        startActivityForResult(intent, OUTLINE_REQUEST);
                    }
                }
            });
        } else {
            mOutlineButton.setVisibility(View.GONE);
        }

        // Reenstate last state if it was recorded
        SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        int lastPage = prefs.getInt("page" + mFileName, 0);
        mDocView.setDisplayedViewIndex(lastPage);
        if (mPageNumber < core.countPages()) {
            mDocView.setDisplayedViewIndex(mPageNumber);
        } else {
            mDocView.setDisplayedViewIndex(0);
        }
        if (savedInstanceState == null || !savedInstanceState.getBoolean("ButtonsHidden", false))
            showButtons();

        if (savedInstanceState != null && savedInstanceState.getBoolean("SearchMode", false))
            searchModeOn();

        if (savedInstanceState != null && savedInstanceState.getBoolean("ReflowMode", false))
            reflowModeSet(true);
      /*  ConstraintLayout rl = (ConstraintLayout) rootView.findViewById(R.id.parentLayout);
        rl.removeAllViews();
        layout.addView(mDocView);*/
        //layout.addView(mButtonsView);

        if (getArguments() != null && getArguments().getBoolean(PARAM_SHOW_CONTROLS)) {
            mButtonsView.setVisibility(View.VISIBLE);
        } else {
            mButtonsView.setVisibility(View.GONE);
        }

        // Performs tap event to refresh view.
        Handler handler = new Handler();
        handler.postDelayed(runnable, msRedraw);

        return mDocView;
    }

    private void buildBreadcrumbs(File file, StringBuilder builder) {
        File parent = file.getParent();
        if (parent != null) {
            buildBreadcrumbs(parent, builder);

            if (builder.length() > 0) {
                builder.append(" > ");
            }
            builder.append(parent.getDisplayName());
        }
    }

    public Object onRetainNonConfigurationInstance() {
        MuPDFCore mycore = core;
        core = null;
        return mycore;
    }

    private void reflowModeSet(boolean reflow) {
        mReflow = reflow;
        mDocView.setAdapter(mReflow ? new MuPDFReflowAdapter(mContext, core) : new MuPDFPageAdapter(getActivity(), this, core));
        mReflowButton.setColorFilter(mReflow ? Color.argb(0xFF, 172, 114, 37) : Color.argb(0xFF, 255, 255, 255));
        setButtonEnabled(mAnnotButton, !reflow);
        setButtonEnabled(mSearchButton, !reflow);
        if (reflow) setLinkHighlight(false);
        setButtonEnabled(mLinkButton, !reflow);
        setButtonEnabled(mMoreButton, !reflow);
        mDocView.refresh(mReflow);
    }

    private void toggleReflow() {
        reflowModeSet(!mReflow);
        showInfo(mReflow ? getString(com.artifex.mupdfdemo.R.string.entering_reflow_mode) : getString(com.artifex.mupdfdemo.R.string.leaving_reflow_mode));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            if (mFileName != null && mDocView != null) {
                outState.putString("FileName", mFileName);

                // Store current page in the prefs against the file name,
                // so that we can pick it up each time the file is loaded
                // Other info is needed only for screen-orientation change,
                // so it can go in the bundle
                SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
                edit.commit();
            }

            if (!mButtonsVisible)
                outState.putBoolean("ButtonsHidden", true);

            if (mTopBarMode == TopBarMode.Search)
                outState.putBoolean("SearchMode", true);

            if (mReflow)
                outState.putBoolean("ReflowMode", true);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onPause() {
        super.onPause();

        if (mSearchTask != null)
            mSearchTask.stop();

        if (mFileName != null && mDocView != null) {
            SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
            edit.commit();
        }
    }

    public void onDestroy() {
        if (mDocView != null) {
            mDocView.applyToChildren(new ReaderView.ViewMapper() {
                public void applyToView(View view) {
                    ((MuPDFView) view).releaseResources();
                }
            });
            mDocView.setEventCallback(null);
        }
        if (core != null)
            core.onDestroy();
        if (mAlertTask != null) {
            mAlertTask.cancel(true);
            mAlertTask = null;
        }
        eventCallback = null;
        core = null;

        // Android is not releasing the memory recycled from the bitmaps on certain circumstances, which leads to OutOfMemory errors.
        // Somehow the gc is not called automatically in those situations...
        //
        System.gc();

        super.onDestroy();
    }

    private void setButtonEnabled(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setColorFilter(enabled ? Color.argb(255, 255, 255, 255) : Color.argb(255, 128, 128, 128));
    }

    private void setLinkHighlight(boolean highlight) {
        mLinkHighlight = highlight;
        // LINK_COLOR tint
        mLinkButton.setColorFilter(highlight ? Color.argb(0xFF, 172, 114, 37) : Color.argb(0xFF, 255, 255, 255));
        // Inform pages of the change.
        mDocView.setLinksEnabled(highlight);
    }

    private void showButtons() {
        if (core == null)
            return;
        if (!mButtonsVisible) {
            mButtonsVisible = true;
            // Update page number text and slider
            int index = mDocView.getDisplayedViewIndex();
            updatePageNumView(index);
            mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
            mPageSlider.setProgress(index * mPageSliderRes);
            if (mTopBarMode == TopBarMode.Search) {
                mSearchText.requestFocus();
                showKeyboard();
            }

            Animation anim = new TranslateAnimation(0, 0, -mTopBarSwitcher.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                }
            });
            mTopBarSwitcher.startAnimation(anim);

            anim = new TranslateAnimation(0, 0, mPageSlider.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mPageSlider.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mPageNumberView.setVisibility(View.VISIBLE);
                }
            });
            mPageSlider.startAnimation(anim);
        }
    }

    private void hideButtons() {
        if (mButtonsVisible) {
            mButtonsVisible = false;
            hideKeyBoard();

            Animation anim = new TranslateAnimation(0, 0, 0, -mTopBarSwitcher.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.INVISIBLE);
                }
            });
            mTopBarSwitcher.startAnimation(anim);

            anim = new TranslateAnimation(0, 0, 0, mPageSlider.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mPageNumberView.setVisibility(View.INVISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mPageSlider.setVisibility(View.INVISIBLE);
                }
            });
            mPageSlider.startAnimation(anim);
        }
    }

    private void searchModeOn() {
        if (mTopBarMode != TopBarMode.Search) {
            mTopBarMode = TopBarMode.Search;
            //Focus on EditTextWidget
            mSearchText.requestFocus();
            showKeyboard();
            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        }
    }

    private void searchModeOff() {
        if (mTopBarMode == TopBarMode.Search) {
            mTopBarMode = TopBarMode.Main;
            hideKeyBoard();
            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
            SearchTaskResult.set(null);
            // Make the ReaderView act on the change to mSearchTaskResult
            // via overridden onChildSetup method.
            mDocView.resetupChildren();
        }
    }

    private void updatePageNumView(int index) {
        if (core == null)
            return;
        mPageNumberView.setText(String.format("%d / %d", index + 1, core.countPages()));
    }

    private void printDoc() {
        if (!core.fileFormat().startsWith("PDF")) {
            showInfo(getString(com.artifex.mupdfdemo.R.string.format_currently_not_supported));
            return;
        }

        Intent myIntent = getActivity().getIntent();
        Uri docUri = myIntent != null ? myIntent.getData() : null;

        if (docUri == null) {
            showInfo(getString(com.artifex.mupdfdemo.R.string.print_failed));
        }

        if (docUri.getScheme() == null)
            docUri = Uri.parse("file://" + docUri.toString());

        Intent printIntent = new Intent(mContext, PrintDialogActivity.class);
        printIntent.setDataAndType(docUri, "aplication/pdf");
        printIntent.putExtra("title", mFileName);
        startActivityForResult(printIntent, PRINT_REQUEST);
    }

    private void showInfo(String message) {
        mInfoView.setText(message);

        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentApiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            SafeAnimatorInflater safe = new SafeAnimatorInflater(getActivity(), com.artifex.mupdfdemo.R.animator.info, (View) mInfoView);
        } else {
            mInfoView.setVisibility(View.VISIBLE);
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mInfoView.setVisibility(View.INVISIBLE);
                }
            }, 500);
        }
    }

    private void makeButtonsView() {
        mButtonsView = getActivity().getLayoutInflater().inflate(com.artifex.mupdfdemo.R.layout.buttons, null);
        mFilenameView = (TextView) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.docNameText);
        mPageSlider = (SeekBar) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.pageSlider);
        mPageNumberView = (TextView) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.pageNumber);
        mInfoView = (TextView) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.info);
        mSearchButton = (ImageButton) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.searchButton);
        mReflowButton = (ImageButton) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.reflowButton);
        mOutlineButton = (ImageButton) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.outlineButton);
        mAnnotButton = (ImageButton) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.editAnnotButton);
        mAnnotTypeText = (TextView) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.annotType);
        mTopBarSwitcher = (ViewAnimator) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.switcher);
        mSearchBack = (ImageButton) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.searchBack);
        mSearchFwd = (ImageButton) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.searchForward);
        mSearchText = (EditText) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.searchText);
        mLinkButton = (ImageButton) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.linkButton);
        mMoreButton = (ImageButton) mButtonsView.findViewById(com.artifex.mupdfdemo.R.id.moreButton);
        mTopBarSwitcher.setVisibility(View.INVISIBLE);
        mPageNumberView.setVisibility(View.INVISIBLE);
        mInfoView.setVisibility(View.INVISIBLE);
        mPageSlider.setVisibility(View.INVISIBLE);
    }

    public void OnMoreButtonClick(View v) {
        mTopBarMode = TopBarMode.More;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnCancelMoreButtonClick(View v) {
        mTopBarMode = TopBarMode.Main;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnPrintButtonClick(View v) {
        printDoc();
    }

    public void OnCopyTextButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.CopyText;
        mDocView.setMode(MuPDFReaderView.Mode.Selecting);
        mAnnotTypeText.setText(getString(com.artifex.mupdfdemo.R.string.copy_text));
        showInfo(getString(com.artifex.mupdfdemo.R.string.select_text));
    }

    public void OnEditAnnotButtonClick(View v) {
        mTopBarMode = TopBarMode.Annot;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnCancelAnnotButtonClick(View v) {
        mTopBarMode = TopBarMode.More;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnHighlightButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.Highlight;
        mDocView.setMode(MuPDFReaderView.Mode.Selecting);
        mAnnotTypeText.setText(com.artifex.mupdfdemo.R.string.highlight);
        showInfo(getString(com.artifex.mupdfdemo.R.string.select_text));
    }

    public void OnUnderlineButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.Underline;
        mDocView.setMode(MuPDFReaderView.Mode.Selecting);
        mAnnotTypeText.setText(com.artifex.mupdfdemo.R.string.underline);
        showInfo(getString(com.artifex.mupdfdemo.R.string.select_text));
    }

    public void OnStrikeOutButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.StrikeOut;
        mDocView.setMode(MuPDFReaderView.Mode.Selecting);
        mAnnotTypeText.setText(com.artifex.mupdfdemo.R.string.strike_out);
        showInfo(getString(com.artifex.mupdfdemo.R.string.select_text));
    }

    public void OnInkButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.Ink;
        mDocView.setMode(MuPDFReaderView.Mode.Drawing);
        mAnnotTypeText.setText(com.artifex.mupdfdemo.R.string.ink);
        showInfo(getString(com.artifex.mupdfdemo.R.string.draw_annotation));
    }

    public void OnCancelAcceptButtonClick(View v) {
        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
        if (pageView != null) {
            pageView.deselectText();
            pageView.cancelDraw();
        }
        mDocView.setMode(MuPDFReaderView.Mode.Viewing);
        switch (mAcceptMode) {
            case CopyText:
                mTopBarMode = TopBarMode.More;
                break;
            default:
                mTopBarMode = TopBarMode.Annot;
                break;
        }
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnAcceptButtonClick(View v) {
        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
        boolean success = false;
        switch (mAcceptMode) {
            case CopyText:
                if (pageView != null)
                    success = pageView.copySelection();
                mTopBarMode = TopBarMode.More;
                showInfo(success ? getString(com.artifex.mupdfdemo.R.string.copied_to_clipboard) : getString(com.artifex.mupdfdemo.R.string.no_text_selected));
                break;

            case Highlight:
                if (pageView != null)
                    success = pageView.markupSelection(Annotation.Type.HIGHLIGHT);
                mTopBarMode = TopBarMode.Annot;
                if (!success)
                    showInfo(getString(com.artifex.mupdfdemo.R.string.no_text_selected));
                break;

            case Underline:
                if (pageView != null)
                    success = pageView.markupSelection(Annotation.Type.UNDERLINE);
                mTopBarMode = TopBarMode.Annot;
                if (!success)
                    showInfo(getString(com.artifex.mupdfdemo.R.string.no_text_selected));
                break;

            case StrikeOut:
                if (pageView != null)
                    success = pageView.markupSelection(Annotation.Type.STRIKEOUT);
                mTopBarMode = TopBarMode.Annot;
                if (!success)
                    showInfo(getString(com.artifex.mupdfdemo.R.string.no_text_selected));
                break;

            case Ink:
                if (pageView != null)
                    success = pageView.saveDraw();
                mTopBarMode = TopBarMode.Annot;
                if (!success)
                    showInfo(getString(com.artifex.mupdfdemo.R.string.nothing_to_save));
                break;
        }
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mDocView.setMode(MuPDFReaderView.Mode.Viewing);
    }

    public void OnCancelSearchButtonClick(View v) {
        searchModeOff();
    }

    public void OnDeleteButtonClick(View v) {
        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
        if (pageView != null)
            pageView.deleteSelectedAnnotation();
        mTopBarMode = TopBarMode.Annot;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnCancelDeleteButtonClick(View v) {
        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
        if (pageView != null)
            pageView.deselectAnnotation();
        mTopBarMode = TopBarMode.Annot;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.showSoftInput(mSearchText, 0);
    }

    protected void hideKeyBoard() {
        getActivity().getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        );
    }


    public void search(int direction) {
        hideKeyBoard();
        int displayPage = mDocView.getDisplayedViewIndex();
        SearchTaskResult r = SearchTaskResult.get();
        int searchPage = r != null ? r.pageNumber : -1;
        mSearchTask.go(mSearchText.getText().toString(), direction, displayPage, searchPage);
    }

    @Override
    public void onStart() {
        if (core != null) {
            core.startAlerts();
            createAlertWaiter();
        }

        super.onStart();
        hideKeyBoard();
    }

    @Override
    public void onStop() {
        if (core != null) {
            destroyAlertWaiter();
            core.stopAlerts();
        }

        super.onStop();
    }

    @Override
    public void performPickFor(FilePicker picker) {
        mFilePicker = picker;
        Intent intent = new Intent(mContext, ChoosePDFActivity.class);
        intent.setAction(ChoosePDFActivity.PICK_KEY_FILE);
        startActivityForResult(intent, FILEPICK_REQUEST);
    }


    // @viafirma: Custom methods
    public static MuPDFFragment newInstance(String signBitmapPath, List<PdfBitmap> digitalizedImage, String pathPdf, boolean showControls) {
        MuPDFFragment f = new MuPDFFragment();
        Bundle args = new Bundle();

        if (digitalizedImage != null && digitalizedImage.size() > 0) {
            args.putParcelable(PARAM_DIGITALIZED_IMAGE, digitalizedImage.get(0));
        }

        if (signBitmapPath != null) {
            args.putString(PARAM_SIGN_BITMAP_PATH, signBitmapPath);
        }

        if (pathPdf != null) {
            args.putString(PARAM_PATH_PDF, pathPdf);
        }
        args.putBoolean(PARAM_SHOW_CONTROLS, showControls);
        f.setArguments(args);
        return f;
    }

    public static MuPDFFragment newInstance(String pathPdf, File file, boolean showControls) {
        MuPDFFragment fragment = new MuPDFFragment();
        Bundle args = new Bundle();
        args.putParcelable(BUNDLE_FILE, file);
        args.putString(PARAM_PATH_PDF, pathPdf);
        args.putBoolean(PARAM_SHOW_CONTROLS, showControls);
        fragment.setArguments(args);
        return fragment;

    }

    public static MuPDFFragment newInstance(String signBitmapPath, List<PdfBitmap> digitalizedImage, String pathPdf) {
        return newInstance(signBitmapPath, digitalizedImage, pathPdf, true);
    }

    public static Fragment newInstance(String filePath, File m_file, long fileID, String title, boolean showControls) {
        MuPDFFragment f = new MuPDFFragment();
        f.m_fileID = fileID;
        Bundle args = new Bundle();
        if (fileID != 0) {
            args.putLong(BUNDLE_FILE_ID, fileID);
        }
        if (m_file != null) {
            args.putParcelable(BUNDLE_FILE, m_file);
        }
        if (filePath != null) {
            args.putString(PARAM_PATH_PDF, filePath);
        }
        if (title != null) {
            args.putString(BUNDLE_TITLE, title);
        }
        args.putBoolean(PARAM_SHOW_CONTROLS, showControls);
        f.setArguments(args);
        return f;


    }

    public void setPageNumber(int pageNumber) {
        this.mPageNumber = pageNumber;
        if (mDocView != null && core != null) {
            if (pageNumber < core.countPages()) {
                mDocView.setDisplayedViewIndex(pageNumber);
            }
        }
    }

    public int getCurrentPage() {
        int page = 0;
        if (mDocView != null) {
            page = mDocView.getDisplayedViewIndex();
        }
        return page;
    }


    public void addBitmap(PdfBitmap pdfBitmap) {
        if (mDocView != null) {
            mDocView.addBitmap(pdfBitmap);
        } else {
            Log.e(TAG, "Couldn't add Bitmap. DocView is NULL.");
        }
    }

    public void setPdfBitmapList(Collection<PdfBitmap> pdfBitmaps) {
        this.pdfBitmaps = pdfBitmaps;
        if (mDocView != null) {
            mDocView.setPdfBitmapList(pdfBitmaps);
        }
    }

    public Collection<PdfBitmap> getBitmapList() {
        if (mDocView != null) {
            return mDocView.getBitmapList();
        } else {
            Log.e(TAG, "Couldn't get bitmap list. DocView is NULL.");
            return new HashSet<>();
        }
    }

    public boolean removeBitmapOnPosition(float x, float y) {
        boolean removed = false;
        if (mDocView != null) {
            Point point = new Point((int) x, (int) y);
            removed = mDocView.removeBitmapOnPosition(point);
        } else {
            Log.e(TAG, "Couldn't remove Bitmap. DocView is NULL.");
        }
        return removed;
    }

    ;

    public void setByteArrayPdf(byte[] byteArrayPdf) {
        this.byteArrayPdf = byteArrayPdf;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.mContext = activity;
    }

    public boolean checkSign() {

        MuPDFPageAdapter adapter = (MuPDFPageAdapter) mDocView.getAdapter();
        if (adapter.getNumSignature() > 0 || !mDoSign) {
            return true;
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getResources().getString(com.artifex.mupdfdemo.R.string.noSignOnPdfTitle));
            builder.setMessage(com.artifex.mupdfdemo.R.string.noSignOnPdf);
            builder.setNegativeButton(com.artifex.mupdfdemo.R.string.OkKey, null);
            builder.show();
            return false;
        }
    }

    public DigitalizedEventCallback getEventCallback() {
        return this.eventCallback;
    }

    public void setEventCallback(DigitalizedEventCallback eventCallback) {
        this.eventCallback = eventCallback;
        if (mDocView != null) {
            mDocView.setEventCallback(eventCallback);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Handler handler = new Handler();
        // Excalibur line: if you can replace it, please do it.
        handler.postDelayed(runnable, msRedraw);
    }

    public void updateCurrentPage() {
        if (mDocView != null) {
            mDocView.updateCurrentPage();
        }
    }

    public void redrawAll() {
        if (mDocView != null) {
            mDocView.redrawAll();
        }
    }


    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            redrawTouch();
        }
    };

    private final int msRedraw = 500;

    private void redrawTouch() {
        if (mDocView != null) {
            // Dispatch touch event to view
            mDocView.refreshView();
        }
    }
}
