package jWhiteBoard;


import org.jgroups.*;
import org.jgroups.jmx.JmxConfigurator;
import org.jgroups.stack.AddressGenerator;
import org.jgroups.util.OneTimeAddressGenerator;
import org.jgroups.util.Util;

import javax.management.MBeanServer;
import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;


/**
 * Shared Whiteboard, each new instance joins the same group. Each instance chooses a random color,
 * mouse moves are broadcast to all group members, which then apply them to their canvas<p>
 * @author Bela Ban, Oct 17 2001
 * 
 * 
 * 
 */

public class JWhiteBoard extends ReceiverAdapter implements ActionListener, ChannelListener {
    protected String               groupName="";
    private JChannel               channel=null;
    private int                    memberSize=1;
    private JFrame                 mainFrame=null;
    private JPanel                 subPanel=null;
    private DrawPanel              drawPanel=null;
    private JButton                clearButton, leaveButton;
    private final Random           random=new Random(System.currentTimeMillis());
    private final Font             defaultFont=new Font("Helvetica",Font.PLAIN,12);
    private final Color            drawColor=Color.black;
    private static final Color     backgroundColor=Color.lightGray;
    boolean                        noChannel=false;
    boolean                        jmx;
    private boolean                useState=false;
    private long                   stateTimeout=5000;
    private boolean                use_unicasts=false;
    protected boolean              send_own_state_on_merge=true;
    private final                  List<Address> members=new ArrayList<Address>();


    
    /**
     * Constructor 1
     * 
     * @param props
     * @param no_channel
     * @param jmx
     * @param use_state
     * @param state_timeout
     * @param use_unicasts
     * @param name
     * @param send_own_state_on_merge
     * @param gen
     * @throws Exception
     */
    public JWhiteBoard(String props, boolean no_channel, boolean jmx, boolean use_state, long state_timeout,
                boolean use_unicasts, String name, boolean send_own_state_on_merge, AddressGenerator gen) throws Exception {
        this.noChannel=no_channel;
        this.jmx=jmx;
        this.useState=use_state;
        this.stateTimeout=state_timeout;
        this.use_unicasts=use_unicasts;
        if(no_channel)
            return;

        channel=new JChannel(props);
        if(gen != null)
            channel.addAddressGenerator(gen);
        if(name != null)
            channel.setName(name);
        channel.setReceiver(this);
        channel.addChannelListener(this);
        this.send_own_state_on_merge=send_own_state_on_merge;
    }

    /**
     * Constructor 2
     * @param channel
     * @throws Exception
     */
    public JWhiteBoard(JChannel channel) throws Exception {
        this.channel=channel;
        channel.setReceiver(this);
        channel.addChannelListener(this);
    }


    /**
     * Constructor 3
     * @param channel
     * @param use_state: Save state of Group
     * @param state_timeout: State time out
     * @throws Exception
     */
    public JWhiteBoard(JChannel channel, boolean use_state, long state_timeout) throws Exception {
        this.channel=channel;
        channel.setReceiver(this);
        channel.addChannelListener(this);
        this.useState=use_state;
        this.stateTimeout=state_timeout;
    }

    /**
     * Get the name of Group
     * @return Group name
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Set name for Group
     * @param groupName
     */
    public void setGroupName(String groupName) {
        if(groupName != null)
            groupName=groupName;
    }


    /**
     * Main function
     * @param args
     */
    public static void main(String[] args) {
        JWhiteBoard             whiteBoard=null;
        String           props=null;
        boolean          no_channel=false;
        boolean          jmx=true;
        boolean          use_state=false;
        String           group_name=null;
        long             state_timeout=5000;
        boolean          use_unicasts=false;
        String           name=null;
        boolean          send_own_state_on_merge=true;
        AddressGenerator generator=null;

        //Get startup parameters for JWhiteBoard
        for(int i=0; i < args.length; i++) {
        	//Show help
            if("-help".equals(args[i])) {
                help();
                return;
            } 
            //Properties for Channel
            if("-props".equals(args[i])) {
                props=args[++i];
                continue;
            }
            //If existed, use no channel
            if("-no_channel".equals(args[i])) {
                no_channel=true;
                continue;
            } 
            //Use Java Management Extensions or not
            if("-jmx".equals(args[i])) {
                jmx=Boolean.parseBoolean(args[++i]);
                continue;
            }
            //If existed, set name for the Group
            if("-clustername".equals(args[i])) {
                group_name=args[++i];
                continue;
            }
            //If existed, save Group's state 
            if("-state".equals(args[i])) {
                use_state=true;
                continue;
            }
            //If existed, set timeout for state
            if("-timeout".equals(args[i])) {
                state_timeout=Long.parseLong(args[++i]);
                continue;
            }
            if("-bind_addr".equals(args[i])) {
                System.setProperty("jgroups.bind_addr", args[++i]);
                continue;
            }
            if("-use_unicasts".equals(args[i])) {
                use_unicasts=true;
                continue;
            }
            if("-name".equals(args[i])) {
                name=args[++i];
                continue;
            }
            if("-send_own_state_on_merge".equals(args[i])) {
                send_own_state_on_merge=Boolean.getBoolean(args[++i]);
                continue;
            }
            if("-uuid".equals(args[i])) {
                generator=new OneTimeAddressGenerator(Long.valueOf(args[++i]));
                continue;
            }

            help();
            return;
        }

        try {
            whiteBoard=new JWhiteBoard(props, no_channel, jmx, use_state, state_timeout, use_unicasts, name,
                          send_own_state_on_merge, generator);
            if(group_name == null)
                whiteBoard.setGroupName(group_name);
            whiteBoard.go();
        }
        catch(Throwable e) {
            e.printStackTrace(System.err);
            System.exit(0);
        }
    }


    /**
     * Show help on Console screen
     */
    static void help() {
        System.out.print("\nDraw [-help] [-no_channel] [-props <protocol stack definition>]" +
                " [-clustername <name>] [-state] [-timeout <state timeout>] [-use_unicasts] " +
                "[-bind_addr <addr>] [-jmx <true | false>] [-name <logical name>] [-send_own_state_on_merge true|false] " +
                             "[-uuid <UUID>]");
        System.out.print("-no_channel: doesn't use JGroups at all, any drawing will be relected on the " +
                "whiteboard directly");
        System.out.print("-props: argument can be an old-style protocol stack specification, or it can be " +
                "a URL. In the latter case, the protocol specification will be read from the URL\n");
    }


    /**
     * Generate Random Color
     * @return
     */
    private Color selectColor() {
        int red=Math.abs(random.nextInt()) % 255;
        int green=Math.abs(random.nextInt()) % 255;
        int blue=Math.abs(random.nextInt()) % 255;
        return new Color(red, blue, blue);
    }


    /**
     * Send message to members in members list only.
     * @param buf
     * @throws Exception
     */
    private void sendToAll(byte[] buf) throws Exception {
        for(Address mbr: members)
            channel.send(new Message(mbr, buf));
    }


    /**
     * Init JWhiteBoard interface
     * 
     * @throws Exception
     */
    public void go() throws Exception {
        if(!noChannel && !useState)
            channel.connect(groupName);
        mainFrame=new JFrame();
        mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        drawPanel=new DrawPanel(useState);
        drawPanel.setBackground(backgroundColor);
        subPanel=new JPanel();
        mainFrame.getContentPane().add("Center", drawPanel);
        clearButton=new JButton("Clean");
        clearButton.setFont(defaultFont);
        clearButton.addActionListener(this);
        leaveButton=new JButton("Exit");
        leaveButton.setFont(defaultFont);
        leaveButton.addActionListener(this);
        subPanel.add("South", clearButton);
        subPanel.add("South", leaveButton);
        mainFrame.getContentPane().add("South", subPanel);
        mainFrame.setBackground(backgroundColor);
        clearButton.setForeground(Color.blue);
        leaveButton.setForeground(Color.blue);
        mainFrame.pack();
        mainFrame.setLocation(15, 25);
        mainFrame.setBounds(new Rectangle(250, 250));

        if(!noChannel && useState) {
            channel.connect(groupName, null, stateTimeout);
        }
        mainFrame.setVisible(true);
    }

    /**
     * Set Frame's title
     * @param title : frame's title
     */
    void setTitle(String title) {
        String tmp="";
        if(noChannel) {
            mainFrame.setTitle("JWhiteBoard");
            return;
        }
        if(title != null) {
            mainFrame.setTitle(title);
        }
        else {
            if(channel.getAddress() != null)
                tmp+=channel.getAddress();
            tmp+=" (" + memberSize + ")";
            mainFrame.setTitle(tmp);
        }
    }

    void setTitle() {
        setTitle(null);
    }


    /**
     * When receive a message, analyze message content and then execute the command: Draw or Clear
     */
    public void receive(Message msg) {
        byte[] buf=msg.getRawBuffer();
        if(buf == null) {
            System.err.println("[" + channel.getAddress() + "] received null buffer from " + msg.getSrc() +
                    ", headers: " + msg.printHeaders());
            return;
        }

        try {
            DrawCommand comm=(DrawCommand)Util.streamableFromByteBuffer(DrawCommand.class, buf, msg.getOffset(), msg.getLength());
            switch(comm.mode) {
                case DrawCommand.DRAW:
                    if(drawPanel != null)
                        drawPanel.drawPoint(comm);
                    break;
                case DrawCommand.CLEAR:
                    clearPanel();
                default:
                    System.err.println("***** received invalid draw command " + comm.mode);
                    break;
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Execute when new member join or leave Group
     */
    public void viewAccepted(View v) {
        memberSize=v.size();
        if(mainFrame != null)
            setTitle();
        members.clear();
        members.addAll(v.getMembers());

        if(v instanceof MergeView) {
            System.out.println("** " + v);

            // This is a simple merge function, which fetches the state from the coordinator
            // on a merge and overwrites all of its own state
            if(useState && !members.isEmpty()) {
                Address coord=members.get(0);
                Address local_addr=channel.getAddress();
                if(local_addr != null && !local_addr.equals(coord)) {
                    try {

                        // make a copy of our state first
                        Map<Point,Color> copy=null;
                        if(send_own_state_on_merge) {
                            synchronized(drawPanel.state) {
                                copy=new LinkedHashMap<Point,Color>(drawPanel.state);
                            }
                        }
                        System.out.println("fetching state from " + coord);
                        channel.getState(coord, 5000);
                        if(copy != null)
                            sendOwnState(copy); // multicast my own state so everybody else has it too
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        else
            System.out.println("** View=" + v);
    }


    /**
     * Get state of the Group
     */
    public void getState(OutputStream ostream) throws Exception {
        drawPanel.writeState(ostream);
    }

    /**
     * Set state of the Group
     */
    public void setState(InputStream istream) throws Exception {
        drawPanel.readState(istream);
    }

    /* --------------- Callbacks --------------- */



    /**
     * Clear all the content on a whiteboard
     */
    public void clearPanel() {
        if(drawPanel != null)
            drawPanel.clear();
    }

    /**
     * Send Clear command to all members in Group
     */
    public void sendClearPanelMsg() {
        DrawCommand comm=new DrawCommand(DrawCommand.CLEAR);
        try {
            byte[] buf=Util.streamableToByteBuffer(comm);
            if(use_unicasts)
                sendToAll(buf);
            else
                channel.send(new Message(null, null, buf));
        }
        catch(Exception ex) {
            System.err.println(ex);
        }
    }


    /**
     * Action when click [Clear] or [Leave] button
     */
    public void actionPerformed(ActionEvent e) {
        String     command=e.getActionCommand();
        if("Clear".equals(command)) {
            if(noChannel) {
                clearPanel();
                return;
            }
            sendClearPanelMsg();
        }
        else if("Leave".equals(command)) {
            stop();
        }
        else
            System.out.println("Unknown action");
    }


    /**
     * Leave Group and close JWhiteBoard
     */
    public void stop() {
        if(!noChannel) {
            try {
                channel.close();
            }
            catch(Exception ex) {
                System.err.println(ex);
            }
        }
        mainFrame.setVisible(false);
        mainFrame.dispose();
    }

    /**
     * Send State (content on WhiteBoard) to all members of Group
     * @param copy
     */
    protected void sendOwnState(final Map<Point,Color> copy) {
        if(copy == null)
            return;
        for(Point point: copy.keySet()) {
            // we don't need the color: it is our draw_color anyway
            DrawCommand comm=new DrawCommand(DrawCommand.DRAW, point.x, point.y, drawColor.getRGB());
            try {
                byte[] buf=Util.streamableToByteBuffer(comm);
                if(use_unicasts)
                    sendToAll(buf);
                else
                    channel.send(new Message(null, buf));
            }
            catch(Exception ex) {
                System.err.println(ex);
            }
        }
    }


    /* ------------------------------ ChannelListener interface -------------------------- */

    /**
     * Execute when connected to Group
     */
    public void channelConnected(Channel channel) {
        if(jmx) {
            Util.registerChannel((JChannel)channel, "jgroups");
        }
    }

    /**
     * Execute when disconnected from Group
     */
    public void channelDisconnected(Channel channel) {
        if(jmx) {
            MBeanServer server=Util.getMBeanServer();
            if(server != null) {
                try {
                    JmxConfigurator.unregisterChannel((JChannel)channel,server,groupName);
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void channelClosed(Channel channel) {

    }


    /* --------------------------- End of ChannelListener interface ---------------------- */


    /**
     * DrawPanel 
     * 
     */
    protected class DrawPanel extends JPanel implements MouseMotionListener {
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		protected final Dimension         preferred_size=new Dimension(235, 170);
        protected Image                   img; // for drawing pixels
        protected Dimension               d, imgsize;
        protected Graphics                gr;
        protected final Map<Point,Color>  state;


        /**
         * Constructor
         * @param use_state
         */
        public DrawPanel(boolean use_state) {
            if(use_state)
                state=new LinkedHashMap<Point,Color>();
            else
                state=null;
            createOffscreenImage(false);
            addMouseMotionListener(this);
            addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) {
                    if(getWidth() <= 0 || getHeight() <= 0) return;
                    createOffscreenImage(false);
                }
            });
        }

        /**\
         * Draw saved State (color dots on panel) on WhiteBoard
         * @param outstream
         * @throws IOException
         */
        public void writeState(OutputStream outstream) throws IOException {
            if(state == null)
                return;
            synchronized(state) {
                DataOutputStream dos=new DataOutputStream(new BufferedOutputStream(outstream));
                // DataOutputStream dos=new DataOutputStream(outstream);
                dos.writeInt(state.size());
                for(Map.Entry<Point,Color> entry: state.entrySet()) {
                    Point point=entry.getKey();
                    Color col=entry.getValue();
                    dos.writeInt(point.x);
                    dos.writeInt(point.x);
                    dos.writeInt(col.getRGB());
                }
                dos.flush();
                System.out.println("wrote " + state.size() + " elements");
            }
        }

        /**
         * Read State (color dots) from input stream
         * @param instream
         * @throws IOException
         */
        public void readState(InputStream instream) throws IOException {
            DataInputStream in=new DataInputStream(new BufferedInputStream(instream));
            Map<Point,Color> new_state=new LinkedHashMap<Point,Color>();
            int num=in.readInt();
            for(int i=0; i < num; i++) {
                Point point=new Point(in.readInt(), in.readInt());
                Color col=new Color(in.readInt());
                new_state.put(point, col);
            }

            synchronized(state) {
                state.clear();
                state.putAll(new_state);
                System.out.println("read " + state.size() + " elements");
                createOffscreenImage(true);
            }
        }


        final void createOffscreenImage(boolean discard_image) {
            d=getSize();
            if(discard_image) {
                img=null;
                imgsize=null;
            }
            if(img == null || imgsize == null || imgsize.width != d.width || imgsize.height != d.height) {
                img=createImage(d.width, d.height);
                if(img != null) {
                    gr=img.getGraphics();
                    if(gr != null && state != null) {
                        drawState();
                    }
                }
                imgsize=d;
            }
            repaint();
        }


        /* ---------------------- MouseMotionListener interface------------------------- */

        public void mouseMoved(MouseEvent e) {}

        /**
         * When do a mouse drag, get coordinates ( X and Y) of the mouse, then send Draw command as a message to member of Group
         */
        public void mouseDragged(MouseEvent e) {
            int                 x=e.getX(), y=e.getX();
            DrawCommand         comm=new DrawCommand(DrawCommand.DRAW, x, y, drawColor.getRGB());

            if(noChannel) {
                drawPoint(comm);
                return;
            }

            try {
                byte[] buf=Util.streamableToByteBuffer(comm);
                if(use_unicasts)
                    sendToAll(buf);
                else
                    channel.send(new Message(null, null, buf));
            }
            catch(Exception ex) {
                System.err.println(ex);
            }
        }

        /* ------------------- End of MouseMotionListener interface --------------------- */


        /**
         * Adds pixel to queue and calls repaint() whenever we have MAX_ITEMS pixels in the queue
         * or when MAX_TIME msecs have elapsed (whichever comes first). The advantage compared to just calling
         * repaint() after adding a pixel to the queue is that repaint() can most often draw multiple points
         * at the same time.
         */
        public void drawPoint(DrawCommand c) {
            if(c == null || gr == null) 
            	return;
            Color col=new Color(c.rgb);
            gr.setColor(col);
            gr.fillOval(c.x, c.y, 10, 10);
            repaint();
            if(state != null) {
                synchronized(state) {
                    state.put(new Point(c.x, c.y), col);
                }
            }
        }


        /**
         * Clear all contents
         */
        public void clear() {
            if(gr == null) return;
            gr.clearRect(0, 0, getSize().width, getSize().height);
            repaint();
            if(state != null) {
                synchronized(state) {
                    state.clear();
                }
            }
        }


        /** Draw the entire panel from the state */
        @SuppressWarnings("rawtypes")
		public void drawState() {
            // clear();
            Map.Entry entry;
            Point pt;
            Color col;
            synchronized(state) {
                for(Iterator it=state.entrySet().iterator(); it.hasNext();) {
                    entry=(Map.Entry)it.next();
                    pt=(Point)entry.getKey();
                    col=(Color)entry.getValue();
                    gr.setColor(col);
                    gr.fillOval(pt.x, pt.y, 10, 10);

                }
            }
            repaint();
        }


        public Dimension getPreferredSize() {
            return preferred_size;
        }


        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if(img != null) {
                g.drawImage(img, 0, 0, null);
            }
        }

    }

}
