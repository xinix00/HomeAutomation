package homeautomation.lights.provider.hue;

import homeautomation.lights.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;

import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;

import com.google.gson.*;

public class HueLightsProvider 
	implements ILightsProvider {
	
	private HueLightsConfig config;
	private Hashtable<String, HueLightJson> lightConfigs;
	protected Queue<HueLightChange> lightChanges;
	
	public class HueLightsChanger extends Thread
	{
		private HueLightsProvider provider;
		public HueLightsChanger(HueLightsProvider provider)
		{
			this.provider = provider;
		}
		public void run()
		{
			while(true)
			{
				try {
					HueLightChange lightChange;
					while((lightChange = this.provider.lightChanges.poll()) != null)
					{
						new HueLightsRequest(this.provider, lightChange).start();
						Thread.sleep(50);
					}
				
					Thread.sleep(50);
				} catch (Exception e) {

				}
			}
		}
	}
	
	public class HueLightsRequest extends Thread
	{
		private HueLightsProvider provider;
		private HueLightChange change;
		
		public HueLightsRequest(HueLightsProvider provider, HueLightChange change)
		{
			this.provider = provider;
			this.change = change;
		}
		
		public void run()
		{
			this.provider.SetLight(this.change);
		}
		
	}
	
	public HueLightsProvider(HueLightsConfig config)
	{
		this.config = config;
		this.lightConfigs = new Hashtable<String, HueLightJson>();
		this.lightChanges = new LinkedList<HueLightChange>();
		new HueLightsChanger(this).start();
	}
	
	public boolean IsRegistered() throws Exception
	{
		// -- Create request
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet("http://" + this.config.getAddress() + "/api/" + this.config.getUsername() + "/lights");
	
		try {
			// -- Read response
			HttpResponse response = client.execute(request);
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			
			// - Parse
			JsonParser parser = new JsonParser();
			JsonElement element = parser.parse(rd);
			
			// - Check for error:
			boolean isError = element.isJsonArray();
			
			if(isError) {
				JsonElement errorElement = element.getAsJsonArray().get(0).getAsJsonObject().get("error");
				
				if(errorElement != null)
				{
					int errorCode = errorElement.getAsJsonObject().get("type").getAsInt();
					switch(errorCode)
					{
						case 1:
							return false;
						default:
							throw new Exception("Unknown error code.");
					}
				}
			}
			
			return true;
		} catch (IOException e) {

		}
		
		return false;
	}
	
	public HueRegisterStates Register()
	{
		// -- Create request
		HttpClient client = new DefaultHttpClient();
		HttpPost request = new HttpPost("http://" + this.config.getAddress() + "/api");
		
		try {
			// Create JsonObject
			JsonObject jsonObj = new JsonObject();
			jsonObj.addProperty("username", this.config.getUsername());
			jsonObj.addProperty("devicetype", this.config.getDeviceType());
			
			// Append to request
			StringEntity entity = new StringEntity(jsonObj.toString(), Charset.forName("UTF-8"));
			request.setEntity(entity);
			
			// -- Read response
			HttpResponse response = client.execute(request);
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			
			// - Parse
			JsonParser parser = new JsonParser();
			JsonElement element = parser.parse(rd);
			JsonObject object = element.getAsJsonArray().get(0).getAsJsonObject();
			
			// - Check for error:
			boolean isError = object.get("error") != null;
			
			if(isError){
				int errorCode = object.get("error").getAsJsonObject().get("type").getAsInt();
				switch(errorCode)
				{
					case 101:
						return HueRegisterStates.Unregistered;
					default:
						return HueRegisterStates.Error;
				}
			}
			else {
				return HueRegisterStates.Registered;
			}
			
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
		
		return HueRegisterStates.Error;
	}
	
	@Override
	public void Initialize() {
		
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet("http://" + this.config.getAddress() + "/api/" + this.config.getUsername());
		Gson gson = new Gson();
		
		try {
			
			// -- Read response
			HttpResponse response = client.execute(request);
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			
			// - Parse
			JsonParser parser = new JsonParser();
			JsonElement element = parser.parse(rd);
			
			System.out.println("Inintializing...");
			System.out.println("<< " + element.toString());
			
			Set<Entry<String,JsonElement>> entries = element.getAsJsonObject().get("lights").getAsJsonObject().entrySet();
			
			for(Entry<String,JsonElement> entry : entries) {
				System.out.println("Light: " + entry.getValue());
				
				JsonObject light = entry.getValue().getAsJsonObject();
				HueLightJson lightConfig = gson.fromJson(light.get("state"), HueLightJson.class);
				
				this.lightConfigs.put(entry.getKey(), lightConfig);
			}
			
		} catch (IOException e) {

		}
	}
	
	@Override
	public List<ILight> getLights() {
		List<ILight> lights = new ArrayList<ILight>();
		for(Entry<String, HueLightJson> entry : this.lightConfigs.entrySet())
		{
			lights.add(new HueLight(entry.getKey(), this));
		}
		return lights;
	}
	
	public void AddLightChange(String key, HueLightJson toSet) {
		
		HueLightChange change = new HueLightChange(key, toSet);
		this.lightChanges.add(change);
	}

	protected void SetLight(HueLightChange change)
	{
		// -- Create request
		HttpClient client = new DefaultHttpClient();
		HttpPut request = new HttpPut("http://" + this.config.getAddress() + "/api/" + this.config.getUsername() + "/lights/" + change.getKey() +"/state");
		Gson gson = new Gson();
		
		// Append to request
		StringEntity entity;
		try {
			String toSend = gson.toJson(change.getState());
			entity = new StringEntity(toSend, Charset.forName("UTF-8"));
			request.setEntity(entity);
			
			System.out.println(">> " + toSend);
			
			//HttpResponse response = 
					client.execute(request);
			//BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			
			// - Parse
			//JsonParser parser = new JsonParser();
			//JsonElement element = parser.parse(rd);
			
			//System.out.println("<< " + element.toString());
		} catch (Exception e) {
			
		}
	}

}
