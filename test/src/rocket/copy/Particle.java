package rocket.copy;

public class Particle {

	// location
	public float x, y, z;

	// velocity
	public float dx, dy, dz;
	
	// color
	public float red, green, blue;

	// time to live
	public float timeToLive;

	public Particle() {	
	}

	// the constructor which also assigns location
	public Particle(float newx, float newy, float newz) {
		super();
		this.x = newx;
		this.y = newy;
		this.z = newz;
	}
	
	// constructor to assign location and velocity
	public Particle(float newx, float newy, float newz, float newdx, float newdy, float newdz) {
		super();
		this.x = newx;
		this.y = newy;
		this.z = newz;
		this.dx = newdx;
		this.dy = newdy;
		this.dz = newdz;
	}
}
