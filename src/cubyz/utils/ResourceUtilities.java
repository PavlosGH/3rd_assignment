package cubyz.utils;

import java.io.IOException;

import cubyz.api.Resource;
import pixelguys.json.JsonObject;
import pixelguys.json.JsonParser;

public final class ResourceUtilities {
	private ResourceUtilities() {} // No instances allowed.
	
	public static class EntityModel {
		public String parent;
		public String model;
		public String texture;
	}
	
	public static EntityModel loadEntityModel(Resource entity) throws IOException {
		String path = "assets/" + entity.getMod() + "/models/entity/" + entity.getID() + ".json";
		
		EntityModel model = new EntityModel();
		JsonObject obj = JsonParser.parseObjectFromFile(path);
		model.parent = obj.getString("parent", null);
		JsonObject jsonModel = obj.getObject("model");
		if (jsonModel == null) {
			throw new IOException("Missing \"model\" entry from model " + entity);
		}
		model.model = jsonModel.getString("path", "");
		model.texture = jsonModel.getString("texture", "");
		
		if (model.parent != null) {
			if (model.parent.equals(entity.toString())) {
				throw new IOException("Cannot have itself as parent");
			}
			EntityModel parent = loadEntityModel(new Resource(model.parent));
			Utilities.copyIfNull(model, parent);
		}
		
		return model;
	}	
}
