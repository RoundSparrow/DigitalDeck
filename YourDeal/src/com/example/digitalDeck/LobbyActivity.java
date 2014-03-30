package com.example.digitalDeck;

import com.example.yourdeal.R;

import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.view.ViewGroup.LayoutParams;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import org.json.*;

import javax.jmdns.*;


/**LobbyActivty
 * @author Bradley Johns
 * Joins the user to the list of players in the game and starts
 * the server or client depending on whether or not the user is 
 * hosting the game or joining. Also initiates all socket connections
 * and initiates the game variable. Also does everything else. This
 * class is magic. I love it no matter how broken it is. This documentation
 * is starting to becoming rambling, you have my fullest apologies possible
 * employers who may be reading this.
 */
public class LobbyActivity extends Activity {
    Handler updateConversationHandler;

    /**onCreate
     * @param savedInstanceState used for super.onCreate()
     * Initializes the screen as well as initializes the client
     * or server depending on whether or not the given user hosted
     * the lobby as well as initializes basic variables
     */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lobby);
		
        updateConversationHandler = new Handler();

        if (YourDealApplication.networkingDelegate.isHostingGame()) {
            //Make the start button visible to the host
            Button start = (Button)findViewById(R.id.start);
            start.setVisibility(View.VISIBLE);
        }
        
        drawPlayers();

		// Show the Up button in the action bar.
		setupActionBar();
	}
    
    /**onDestroy
     * Stops JmDNS service when window is closed
     */
    @Override
	protected void onDestroy() {
    	super.onDestroy();
    	//TODO shut down JmDNS
    }
    
    /**onStop
     * destroys JmDNS as well as closes sockets
     * when the application is stopped
     */
    @Override
	protected void onStop() {
    	super.onStop();
    	//TODO shut down JmDNS
    	//TODO close sockets
    }

	/**
	 * Set up the {@link android.app.ActionBar}.
	 */
	private void setupActionBar() {

		getActionBar().setDisplayHomeAsUpEnabled(true);

	}

	/**onCreateOptionsMenu
	 * @param menu the menu to be created
	 * starts and inflates the action bar
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.lobby, menu);
		return true;
	}

	/**onOptionsItemSelected
	 * @param item the item that was selected
	 * sends the user to the specified page for the
	 * item that was selected in the app
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
        case R.id.action_settings:
            Intent toSettings = new Intent(this, SettingsActivity.class);
            startActivity(toSettings);
            return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**drawPlayers
	 * iterates through the gamePlayers string arraylist
	 * and draws the display name of each user currently
	 * in the lobby to the screen
	 */
    public void drawPlayers() {
        TableLayout table = (TableLayout)findViewById(R.id.currentPlayers);
        table.removeAllViews(); //Clear all previous views upon refresh
        LayoutParams tableParams = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);

        //Create and add new rows with player info
        for (Player aPlayer : YourDealApplication.game.getPlayers()) {
            //Draw the specified player
            TableRow row = new TableRow(this);
            row.setLayoutParams(tableParams);
            TextView name = new TextView(this);
            name.setText((String)aPlayer.get("name"));
            name.setTextAppearance(this, android.R.style.TextAppearance_Large);
            name.setPadding(0,0,0,10);
            row.addView(name);
            row.setPadding(0,20,0,0);
            table.addView(row);

            //Draw a line beneath the text
            View line = new View(this);
            line.setLayoutParams(new TableRow.LayoutParams(LayoutParams.FILL_PARENT, 1));
            line.setBackgroundColor(Color.parseColor("#808080"));
        }
        setTitle(YourDealApplication.game.getTitle());
    }

    /**startGame
     * @param view the view that was activated to start the game
     * Begins the game itself if the game has the required number
     * of players
     */
    public void startGame(View view) {
        Intent toUI = new Intent(this, EuchreUIActivity.class);
        YourDealApplication.game.start();
        startActivity(toUI);
    }

    /***************************************************************************************/

	class UpdateUIThread implements Runnable {
		/**updateUIThread
		 * called on a change in input, specifies a string and a
		 * socket that the string came from. If the string containing
		 * the information must be removed from the players list the
		 * thread removes it, otherwise it adds the player to the list
		 * and updates the UI of the server and clients accordingly
		 */
		private String msg;
		private Socket clientSocket;
		
		/**updateUIThread constructor
		 * @param str the string representing the packet received
		 * @param socket the socket the packet was received from
		 * initializes the variables to be used by the run() method
		 */
		public UpdateUIThread(String str, Socket socket) {
			this.msg = str;
			clientSocket = socket;
		}

		/**run
		 * adds or removes the player from the lobby accordingly
		 * If the user is the server it sends out the change in
		 * lobby information to the rest of the users
		 */
		@Override
		public void run() {
			String newPlayer = null;
			String toRemove = null;
			try {
				JSONObject recieved = new JSONObject(msg);
				newPlayer = recieved.get("addPlayer").toString();
				toRemove = recieved.get("removePlayer").toString();
			} catch(JSONException e) {
				e.printStackTrace();
			}
			if (newPlayer != null) { //Add the player to the list
				gamePlayers.add(newPlayer);
				Hashtable<String, String> newPHash = new Hashtable<String, String>();
				newPHash.put("target", "game");
				newPHash.put("addPlayer", newPlayer);
				JSONObject toSend = new JSONObject(newPHash);
				if (isHost) { //In the case of a new client send it existing information
					for (Socket s : outputs) {
						if (s.equals(clientSocket)) continue;
						sendMessage(s, toSend.toString());
					}
					for (int i = 0; i < gamePlayers.size(); i++) {
						Hashtable<String, String> table = new Hashtable<String, String>();
						table.put("target", "game");
						table.put("addPlayer", gamePlayers.get(i));
						final JSONObject JSON = new JSONObject(table);
						try { //Send display names to client
							runOnUiThread(new Runnable() {
				                public void run() {
				                	sendMessage(clientSocket, JSON.toString();
				                }
				            });
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
				
			} 
			if (toRemove != null) {
				gamePlayers.remove(toRemove);
			}
			runOnUiThread(new Runnable() { //Update the UI on the main thread
                public void run() {
                   drawPlayers();
                }
	        });
			if (isHost) {
				//Send out update to clients
				Hashtable<String, Object> toSend = new Hashtable<String, Object>();
				toSend.put("target", "game");
				JSONObject JSON = new JSONObject(toSend);
				if (newPlayer != null) {
					System.out.println("added player: " + newPlayer);
				}
				for (int i = 0; i < outputs.size(); i++) {
					Socket s = outputs.get(i);
					System.out.println("Sending message: " + JSON.toString());
					sendMessage(s, JSON.toString());
				}
			}
		}
	}
}
