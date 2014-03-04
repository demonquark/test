package rocket;

public class Particle {

	// location
	public int x, y;
	
	// time to live
	public int timeToLive;

	public Particle() {	}

	// the constructor which also assigns location
	public Particle(int newx, int newy) {
		super();
		this.x = newx;
		this.y = newy;
	}
}
