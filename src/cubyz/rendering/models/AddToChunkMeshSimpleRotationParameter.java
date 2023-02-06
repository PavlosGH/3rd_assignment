package cubyz.rendering.models;

import cubyz.utils.VertexAttribList;
import cubyz.utils.datastructures.IntSimpleList;

public class AddToChunkMeshSimpleRotationParameter {
	public int x;
	public int y;
	public int z;
	public int[] directionMaps;
	public boolean[] directionInversions;
	public int[] textureIndices;
	public int[] lights;
	public byte neighbors;
	public VertexAttribList vertices;
	public IntSimpleList faces;

	public AddToChunkMeshSimpleRotationParameter(int x, int y, int z, int[] directionMaps,
			boolean[] directionInversions, int[] textureIndices, int[] lights, byte neighbors,
			VertexAttribList vertices, IntSimpleList faces) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.directionMaps = directionMaps;
		this.directionInversions = directionInversions;
		this.textureIndices = textureIndices;
		this.lights = lights;
		this.neighbors = neighbors;
		this.vertices = vertices;
		this.faces = faces;
	}
}