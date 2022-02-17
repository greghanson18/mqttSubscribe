package mqttSubscribe;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * A simple subscriber using MQTT Paho. This class implements the MqttCallback
 * interface.
 *
 * @author K. Hui
 * @updated G.Hanson (for purposes of CM4702 assessment)
 *
 */
public class SubTest implements MqttCallback {
	public static void main(String[] args) {
		String topic = "annie/prototype"; // topic
		int qos = 0; // QOS
		String host = "tcp://localhost:1883";// MQTT server URI
		String clientId = "server"; // client ID
		try {
			MqttClient client = new MqttClient(host, clientId);
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setCleanSession(true);
			client.setCallback(new SubTest());
			System.out.println("Connecting to host: " + host);
			client.connect(connOpts);
			System.out.println("Subscribing topic: " + topic);
			client.subscribe(topic, qos);
		} catch (MqttException me) {
			System.out.println("reason " + me.getReasonCode());
			System.out.println("msg " + me.getMessage());
			System.out.println("loc " + me.getLocalizedMessage());
			System.out.println("cause " + me.getCause());
			System.out.println("excep " + me);
			me.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	} // end method

	/**
	 * Method to invoke when connection is lost.
	 */
	@Override
	public void connectionLost(Throwable cause) {
		System.out.println(cause.toString()); //Added by G.Hanson - Give more data if connectionLost
		System.out.println("Connection lost.");
	} // end method

	/**
	 * Method to invoke when a message is received.
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		System.out.println("Topic: " + topic + " Message: " + message); //Uncomment if you want to read the message
		
		//Declare variables
		boolean gatheringData = true;
		boolean result = false;
		
		double avgTime = 0;
		double newNumber = 0;
		double minTime = 1000;
		double maxTime = 0;
		
		int numCompressions;
		int stringBase = 0;
		int stringPeak = 0;
		
		String feedback = "";
		String messageString = message.toString();
		String replyTo = "";
		String studentNumber = "";
			
			//Move through the first part of the message (contains timings between chest compressions
			//until ']' character is found (end of number list)
			for(int i = 1; messageString.charAt(i-1) != ']'; i++) {
				stringBase = i;
				while ((messageString.charAt(i) != ',') && (messageString.charAt(i) != ']')) {
					i++;
				}
				stringPeak = i;
				
				//Extract substring (which should be a number) from the main message string
				newNumber = Double.parseDouble(messageString.substring(stringBase,stringPeak));
				
				//Append new found number to avgTime
				avgTime += newNumber;
				
				//Update other variables				
				if(newNumber < minTime) {
					minTime = newNumber;
				}
				
				if(newNumber > maxTime) {
					maxTime = newNumber;
				}
				
			}
			
			
			//Find where &'s are in string (breakpoints between data)
			int index = 0;
			int[] ampLocation = new int[4];
			for (int i = 1; i < messageString.length(); i++) {
				if (messageString.charAt(i) == '&'){
						ampLocation[index] = i;
						index++;
				}
			}
			
			
			//Get remaining data out of message
			numCompressions = Integer.parseInt(messageString.substring(ampLocation[0]+1,ampLocation[1]));
			studentNumber = messageString.substring(ampLocation[1]+1,ampLocation[2]);
			replyTo = messageString.substring(ampLocation[2]+1, ampLocation[3]);
			
			//Calculations
			avgTime = avgTime / numCompressions;
			
			//Work out if student passed test + feedback
			feedback = getFeedback(numCompressions, avgTime, minTime, maxTime);
			result = getResult(feedback);
			
			//Display data - useful during troubleshooting
			System.out.println("DATA GATHERING FINISHED");
			System.out.println("NumCompressions: " + numCompressions);
			System.out.println("Average: " + avgTime);
			System.out.println("MinTime: " + minTime);
			System.out.println("MaxTime: " + maxTime);
			System.out.println("StudentNum: " + studentNumber);
			System.out.println("Did student pass? " + result);
			System.out.println("Feedback: " + feedback);
			System.out.println("replyTo: " + replyTo);
			
			//Call method to send @POST request
			sendPost(numCompressions, avgTime, minTime, maxTime, studentNumber, result, feedback, replyTo);

	}

	/**
	 * Method to invoke when message delivery is completed.
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		System.out.println("Delivery completed.");
	} // end method
	
	
	//getResult determines if the student passed the test
	private boolean getResult(String feedback){
		
		//Basic logic to determine if student passed test
		if(feedback == "OK") {
		return true;
		}else {
		return false;
		}
		
	}
	
	//getFeedback assembles a human readable string with correct formatting and grammar
	//that tells the student what (if anything) they did wrong.
	private String getFeedback(int num, double avg, double min, double max) {
		
		boolean faultFound = false;
		String feedback = "";
		
		
		if(num < 30) {
			feedback += "Too few compressions";
			faultFound = true;
		}
		
		if(num > 30) {
			feedback += "Too many compressions";
			faultFound = true;
		}
		
		if(avg < 550) {
			if (faultFound == true) {
				feedback += " and t";
			}
			else {
				feedback += "T";
			}
			feedback += "oo slow on compressions";
		}
		
		if(avg > 600) {
			if (faultFound == true) {
				feedback = feedback + " and t";
			}
			else {
				feedback += "T";
			}
			feedback += "oo fast on compressions";
		}
		
		if(feedback == "") {
			feedback = "OK";
		}
		
		return feedback;
		
		
	}
	
	
	//Sends an HTTP @POST request with data encoded in it
	private void sendPost(int num, double avg, double min, double max, String studentNum, boolean pass, String feedback, String replyTo){
		
		//assemble a string with the values in it
		String values = "&studentnumber=" + studentNum + "&compressions=" + num + "&minimum=" + min + "&maximum=" + max + "&average=" + avg + "&didpass=" + pass + "&feedback=" + feedback + "&";
        System.out.println("VALUES FOR POST REQUEST: " + values); //Uncomment if you want to read the message

        try {
        var objectMapper = new ObjectMapper();
        String requestBody = objectMapper
                .writeValueAsString(values);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
        		.uri(URI.create("http://192.168.178.248:8080/annie/annie/api")) //CHANGE IP ADDRESS IF NECCESSARY
                .headers("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

  
        
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body()); //Uncomment if you want to read the message
        
        //Send reply to MQTT server
        sendReply(feedback, replyTo, studentNum);
        }
        catch(Exception e){
        	System.out.println("***** ERROR ******");
        	System.out.println(e.toString()); //Display a helpful error message
        }
	
	}
	
	//Sends a message to MQTT server - contains feedback for student
	public void sendReply (String content, String hostname, String studentNumber){

	//Append information to content string
	content += "&" + hostname + "&" + studentNumber + "&";
		
	//IMPORTANT - Set topic to include hostname - what way each annie can identify feedback aimed at it
	String topic="anniereply/" + hostname;
	
	int qos=0; //QOS
	String host="tcp://localhost:1883"; //CHANGE IP ADDRESS IF NEEDED
	String clientId="reply";
	MemoryPersistence persistence=new MemoryPersistence();
	try {
	 MqttClient client=new MqttClient(host,clientId,persistence);
	 MqttConnectOptions connOpts=new MqttConnectOptions();
	 connOpts.setCleanSession(true);
	 client.connect(connOpts);
	 MqttMessage message = new MqttMessage(content.getBytes());
	 message.setQos(qos);
	 client.publish(topic, message);
	 client.disconnect();
	} catch(MqttException me)
	{
	 System.out.println("reason "+me.getReasonCode());
	 System.out.println("msg "+me.getMessage());
	 System.out.println("loc "+me.getLocalizedMessage());
	 System.out.println("cause "+me.getCause());
	 System.out.println("excep "+me);
	 me.printStackTrace();
	}
	} //end method

	
} // end class
