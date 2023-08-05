/**

 A simple ArtNet DMX-512 monitor written in Java. Shows a single window that displays
 the values of all DMX channels in a universe. Operates in single-universe or "omni"
 mode (indicating the universe numbers yielding data). Binds to the default NIC, so
 you may need to ensure that the default NIC matches the one ArtNet data is received
 on if you have multiple NICs on your machine (e.g., Ethernet and Wifi).

 Based on the artnet4j fork taken from https://github.com/cansik/artnet4j.

 License: GNU GPL v3 http://www.gnu.org/licenses/gpl.html

 Written by Mike Fahl, http://pixilab.se (because I couldn't find a decent
 ArtNet monitor that ran on Mac/Linux, and I prefer not having to schlep
 around a Windows boat anchor just for this).

*/

package se.pixilab.artnetmonitor;

import ch.bildspur.artnet.ArtNetException;
import ch.bildspur.artnet.ArtNetServer;
import ch.bildspur.artnet.events.ArtNetServerEventAdapter;
import ch.bildspur.artnet.packets.ArtDmxPacket;
import ch.bildspur.artnet.packets.ArtNetPacket;
import ch.bildspur.artnet.packets.PacketType;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.net.SocketException;
import java.text.NumberFormat;
import java.util.BitSet;


public class Monitor extends ArtNetServerEventAdapter {
	public static final int kChannels = 512;
	private ArtNetServer server;
	private MainWindow mWindow;

	public static void main(String argv[]) {

		try {
			// Play nice on my Mac
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("apple.awt.application.name", "ArtNet Monitor");
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			JFrame.setDefaultLookAndFeelDecorated(true);

			new Monitor();
		} catch(ArtNetException anx) {
			System.err.println("ArtNetException " + anx.getMessage());
		} catch (SocketException sex) {
			System.err.println("SocketException " + sex.getMessage());
		} catch (Exception e) {
			System.err.println("Unexpected error " + e.getMessage());
		}
	}

	Monitor() throws SocketException, ArtNetException {
		mWindow = new MainWindow();
		server = new ArtNetServer();
		server.addListener(this);
		server.start(null);
	}

	@Override
	public void artNetPacketReceived(ArtNetPacket packet) {
		if (packet.getType() == PacketType.ART_OUTPUT)
			mWindow.gotDmxData((ArtDmxPacket)packet);
	}
}

/**
 My single window, containing some controls and the DMX data grid.
*/
class MainWindow {
	private DmxDataGrid mDataGrid;

	JRadioButton allUniverses = new JRadioButton("All");
	JRadioButton oneUniverse = new JRadioButton("Specific:");
	JFormattedTextField selUniverseNumField;
	private int mShowUniverse = 0;

	JLabel receivedUniverse = new JLabel("   ");
	int mLastReceivedUniverse = -1;

	MainWindow() {
		JFrame frame = new JFrame("PIXILAB ArtNet Monitor");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		BorderLayout bl = new BorderLayout();
		frame.setLayout(bl);

		FlowLayout fl = new FlowLayout(FlowLayout.CENTER, 5, 10);
		fl.setHgap(fl.getHgap() / 2);
		JPanel buttonPanel = new JPanel(fl);
		ButtonGroup group = new ButtonGroup();
		allUniverses.setSelected(true);
		group.add(allUniverses);
		group.add(oneUniverse);
		buttonPanel.add(new JLabel("Universe:"));
		buttonPanel.add(allUniverses);
		buttonPanel.add(oneUniverse);


		NumberFormat format = NumberFormat.getInstance();
		NumberFormatter formatter = new NumberFormatter(format);
		formatter.setValueClass(Integer.class);
		formatter.setMinimum(0);
		formatter.setMaximum(Integer.MAX_VALUE);
		formatter.setAllowsInvalid(true);
		// If you want the value to be committed on each keystroke instead of focus lost
		formatter.setCommitsOnValidEdit(true);
		selUniverseNumField = new JFormattedTextField(formatter);
		selUniverseNumField.setColumns(3);
		selUniverseNumField.setValue(mShowUniverse);
		buttonPanel.add(selUniverseNumField);
		selUniverseNumField.addPropertyChangeListener("value", evt -> {
			if (selUniverseNumField.isEditValid()) {
				mShowUniverse = Integer.parseInt(evt.getNewValue().toString());
				oneUniverse.setSelected(true);	// Auto-select radiobutton on text change
			}
		});
		buttonPanel.add(new JLabel("Received:"));
		buttonPanel.add(receivedUniverse);

		frame.add(buttonPanel,BorderLayout.NORTH);

		JPanel gridPanel = new JPanel();
		gridPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
		mDataGrid = new DmxDataGrid();
		gridPanel.add(mDataGrid);
		frame.add(gridPanel, BorderLayout.CENTER);

		frame.pack();
		frame.setVisible(true);

		// No need to resize window for now, so lock its size
		Dimension frameSize = frame.getMinimumSize();
		frame.setMinimumSize(frameSize);
		frame.setMaximumSize(frameSize);

		autoSelectOnFocus();
	}

	// https://stackoverflow.com/questions/1178312/how-to-select-all-text-in-a-jformattedtextfield-when-it-gets-focus
	private void autoSelectOnFocus() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", e -> {
			if (e.getNewValue() instanceof JTextField) {
				SwingUtilities.invokeLater(() -> ((JTextField)e.getNewValue()).selectAll());
			}
		});
	}

	void gotDmxData(ArtDmxPacket dmxPacket) {
		final int subnet = dmxPacket.getSubnetID();
		final int universe = dmxPacket.getUniverseID();

		// Display universe number that received data
		if (mLastReceivedUniverse != universe) {
			mLastReceivedUniverse = universe;
			receivedUniverse.setText(Integer.toString(universe));
		}
		if (universe == mShowUniverse || !oneUniverse.isSelected())
			mDataGrid.setNewValues(dmxPacket.getDmxData());
	}

}

class DmxDataGrid extends JComponent {
	static final private int
		kValuesPerRow = 32,
		kCharsPerCell = 3;	// Three decimal digits

	private final int
		mAscent,
		mDescent,
		mLineHeight,
		mCellWidth,
		mCellHeight;	// Two lines per cell, plus some padding
	private final byte[] mNewValues;

	private boolean mHaveSetUp;

	private char mByteChars[] = new char[kCharsPerCell];

	private Image mImage = null;
	private Dimension mSize;

	// Keeps track of changed channels, to repaint those
	private BitSet mChangedChannels = new BitSet(Monitor.kChannels);

	private static final String kHexChars = "0123456789ABCDEF";

	DmxDataGrid() {
		mNewValues = new byte[Monitor.kChannels];

		Font myFont = new Font("monospaced", Font.PLAIN, 14);
		setFont(myFont);
		FontMetrics fm = getFontMetrics(myFont);
		mAscent = fm.getAscent();
		mDescent = fm.getDescent();
		mLineHeight = mAscent + fm.getLeading();
		mCellWidth = fm.charWidth('0') * (kCharsPerCell+1);	// All are same - monospaced
		mCellHeight = (mLineHeight + mDescent) * 2 + (int)Math.floor((float)mLineHeight / 2);
		int rows = Monitor.kChannels / kValuesPerRow;

		mSize = new Dimension(kValuesPerRow * mCellWidth, mCellHeight * rows);
		setMinimumSize(mSize);

		setOpaque(true);
		setPreferredSize(mSize);
	}

	synchronized void setNewValues(byte[] dmxData) {
		int len = Math.min(Monitor.kChannels, dmxData.length);
		// See if this contains any news
		boolean news = false;
		for (int ix = 0; ix < len; ++ix) {
			byte v = dmxData[ix];

			if (v != mNewValues[ix]) {
				news = true;
				mChangedChannels.set(ix);
			}
		}
		if (news) {
			System.arraycopy(dmxData, 0, mNewValues, 0, len);
			repaint();
		}
	}

	@Override
	public void paintComponent(Graphics g) {
		boolean paintChangedOnly = mHaveSetUp;

		if (!mHaveSetUp) {	// Only do this once
			if (g instanceof Graphics2D) {
				// https://www.oracle.com/java/technologies/graphics-performance-improvements.html
				mImage = createImage(mSize.width, mSize.height);
				Graphics2D g2 = (Graphics2D)g;
				g2.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON
				);
			}
			mHaveSetUp = true;
		}
		Graphics gImg = mImage.getGraphics();
		paintValues(gImg, paintChangedOnly);
		g.drawImage(mImage, 0, 0, null);
		gImg.dispose();
	}

	/**
	 Paint ALL values (if drawAll) or channels set in mChangedChannels.
	*/
	private synchronized void paintValues(Graphics g, boolean changedOnly) {
		int ix = 0;
		if (changedOnly) {	// Paint only changed values
			while ((ix = mChangedChannels.nextSetBit(ix)) >= 0)
				paintValue(g, ix++);
			mChangedChannels.clear();
		} else {	// Paint all
			g.setColor(getBackground()); // Fill with background colour
			g.fillRect(0, 0, mSize.width, mSize.height);
			while (ix < Monitor.kChannels) {
				paintChannelNumber(g, ix);
				paintValue(g, ix++);
			}
		}
	}

	private void paintChannelNumber(Graphics g, int ix) {
		int y = (ix / kValuesPerRow) * mCellHeight;
		int x = (ix % kValuesPerRow) * mCellWidth;
		g.setColor(Color.GRAY);
		drawThreeDigits(g, x, y, ix+1);
	}

	/**
	 Paint the value at ix.
	*/
	private void paintValue(Graphics g, int ix) {
		int v = (mNewValues[ix] + 256) & 0xff;
		int y = (ix / kValuesPerRow) * mCellHeight + mLineHeight + mDescent;
		int x = (ix % kValuesPerRow) * mCellWidth;

		// Paint bg from value, with contrasting text
		Color bgColor = new Color(v, v, v);
		g.setColor(bgColor);
		g.fillRect(x, y, mCellWidth, mLineHeight + mDescent);
		g.setColor(v > 0x80 ? Color.BLACK : Color.WHITE);

		drawThreeDigits(g, x, y, v);

	}

	private void drawThreeDigits(Graphics g, int x, int y, int v) {
		mByteChars[0] = nibToHexChar(v / 100);
		mByteChars[1] = nibToHexChar(v / 10 % 10);
		mByteChars[2] = nibToHexChar(v % 10);

		g.drawChars(mByteChars, 0, kCharsPerCell, x, y+mAscent);
	}

	private static char nibToHexChar(int nib) {
		return kHexChars.charAt(nib & 0x0f);
	}
}