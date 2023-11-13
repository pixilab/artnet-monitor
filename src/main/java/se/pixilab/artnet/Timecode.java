/**

 A simple ArtNet timecode monitor. Shows a single window that displays
 the values of the incoming timecode. Binds to the default NIC, so
 you may need to ensure that the default NIC matches the one ArtNet data is received
 on if you have multiple NICs on your machine (e.g., Ethernet and Wifi).

 Based on the artnet4j fork taken from https://github.com/cansik/artnet4j.

 License: GNU GPL v3 http://www.gnu.org/licenses/gpl.html

 Written by Mike Fahl, http://pixilab.se/.

 */

package se.pixilab.artnet;

import ch.bildspur.artnet.ArtNetException;
import ch.bildspur.artnet.ArtNetServer;
import ch.bildspur.artnet.events.ArtNetServerEventAdapter;
import ch.bildspur.artnet.packets.ArtNetPacket;
import ch.bildspur.artnet.packets.ArtTimePacket;
import ch.bildspur.artnet.packets.PacketType;

import javax.swing.*;
import java.awt.*;
import java.net.SocketException;
import java.util.BitSet;


public class Timecode extends ArtNetServerEventAdapter {
	private ArtNetServer server;
	private TimeWindow mWindow;

	public static void main(String argv[]) {

		try {
			// Play nice on MacOS
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("apple.awt.application.name", "ArtNet Monitor");
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			JFrame.setDefaultLookAndFeelDecorated(true);

			new Timecode();
		} catch(ArtNetException anx) {
			System.err.println("ArtNetException " + anx.getMessage());
		} catch (SocketException sex) {
			System.err.println("SocketException " + sex.getMessage());
		} catch (Exception e) {
			System.err.println("Unexpected error " + e.getMessage());
		}
	}

	Timecode() throws SocketException, ArtNetException {
		mWindow = new TimeWindow();
		server = new ArtNetServer();
		server.addListener(this);
		server.start(null);
	}

	@Override
	public void artNetPacketReceived(ArtNetPacket packet) {
		if (packet.getType() == PacketType.ART_TIMECODE)
			mWindow.gotArtnetTime((ArtTimePacket)packet);
	}
}

/**
 My single window, containing some controls and the timecode readout.
 */
class TimeWindow {
	private TimeDataGrid mDataGrid;

	TimeWindow() {
		JFrame frame = new JFrame("PIXILAB ArtNet Timecode Monitor");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		BorderLayout bl = new BorderLayout();
		frame.setLayout(bl);

		FlowLayout fl = new FlowLayout(FlowLayout.CENTER, 5, 10);
		fl.setHgap(fl.getHgap() / 2);

		JPanel gridPanel = new JPanel();
		gridPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
		mDataGrid = new TimeDataGrid();
		gridPanel.add(mDataGrid);
		frame.add(gridPanel, BorderLayout.CENTER);

		frame.pack();
		frame.setVisible(true);

		// No need to resize window for now, so lock its size
		Dimension frameSize = frame.getMinimumSize();
		frame.setMinimumSize(frameSize);
		frame.setMaximumSize(frameSize);
	}

	void gotArtnetTime(ArtTimePacket timePacket) {
		mDataGrid.setNewValues(timePacket);
	}
}

class TimeDataGrid extends JComponent {
	static final private int
		kNumValues = 5,	// H M S f t
		kCharsPerCell = 2;	// Three decimal digits

	private final int
		mAscent,
		mDescent,
		mLineHeight,
		mCellWidth,
		mCellHeight;	// Includes some padding

	private boolean mHaveSetUp;

	private char mByteChars[] = new char[kCharsPerCell];

	private Image mImage = null;
	private Dimension mSize;

	// Keeps track of changed channels, to repaint those
	private BitSet mChangedChannels = new BitSet(TimeDataGrid.kNumValues);
	private int[] values = new int[kNumValues];
	private int[] shownValues = new int[kNumValues];

	TimeDataGrid() {
		Font myFont = new Font("monospaced", Font.PLAIN, 24);
		setFont(myFont);
		FontMetrics fm = getFontMetrics(myFont);
		mAscent = fm.getAscent();
		mDescent = fm.getDescent();
		mLineHeight = mAscent + fm.getLeading();
		mCellWidth = fm.charWidth('0') * (kCharsPerCell+1);	// All are same - monospaced
		mCellHeight = (mLineHeight + mDescent) * 2 + (int)Math.floor((float)mLineHeight / 2);

		mSize = new Dimension(kNumValues * mCellWidth, mCellHeight);
		setMinimumSize(mSize);

		setOpaque(true);
		setPreferredSize(mSize);
	}

	synchronized void setNewValues(ArtTimePacket pkt) {
		int[] values = this.values;
		values[0] = pkt.getHours();
		values[1] = pkt.getMinutes();
		values[2] = pkt.getSeconds();
		values[3] = pkt.getFrames();
		values[4] = pkt.getFrameType();

		// See if this contains any news
		boolean news = false;
		for (int ix = 0; ix < kNumValues; ++ix) {
			int v = values[ix];

			if (v != shownValues[ix]) {
				news = true;
				mChangedChannels.set(ix);
				shownValues[ix] = v;
			}
		}
		if (news)
			repaint();
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
			while (ix < kNumValues) {
				// paintChannelNumber(g, ix);
				paintValue(g, ix++);
			}
		}
	}

	private void paintChannelNumber(Graphics g, int ix) {
		int y = (ix / kNumValues) * mCellHeight;
		int x = (ix % kNumValues) * mCellWidth;
		g.setColor(Color.GRAY);
		drawTwoDigits(g, x, y, ix+1);
	}

	/**
	 Paint the value at ix.
	 */
	private void paintValue(Graphics g, int ix) {
		int v = shownValues[ix];
		int y = (ix / kNumValues) * mCellHeight + mLineHeight + mDescent;
		int x = (ix % kNumValues) * mCellWidth;

		// Paint bg from value, with contrasting text
		g.setColor(Color.BLACK);
		g.fillRect(x, y, mCellWidth, mLineHeight + mDescent);
		g.setColor(Color.WHITE);

		drawTwoDigits(g, x, y, v);
	}

	private void drawTwoDigits(Graphics g, int x, int y, int v) {
		mByteChars[0] = Character.forDigit(v / 10 % 10, 10);
		mByteChars[1] = Character.forDigit(v % 10, 10);

		g.drawChars(mByteChars, 0, kCharsPerCell, x, y+mAscent);
	}

}
