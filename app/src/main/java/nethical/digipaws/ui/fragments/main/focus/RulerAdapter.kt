package nethical.digipaws.ui.fragments.main.focus

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import nethical.digipaws.R

class RulerAdapter(private val maxValue: Int = 180) : RecyclerView.Adapter<RulerAdapter.TickViewHolder>() {

    class TickViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vTick: View = view.findViewById(R.id.vTick)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TickViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ruler_tick, parent, false)
        return TickViewHolder(view)
    }

    override fun onBindViewHolder(holder: TickViewHolder, position: Int) {
        // Make every 5th tick taller and somewhat brighter
        val params = holder.vTick.layoutParams as FrameLayout.LayoutParams
        if ((position + 1) % 5 == 0) {
            params.height = 100 // Taller
            holder.vTick.setBackgroundColor(Color.parseColor("#666666"))
        } else {
            params.height = 60 // Shorter
            holder.vTick.setBackgroundColor(Color.parseColor("#444444"))
        }
        holder.vTick.layoutParams = params
    }

    override fun getItemCount(): Int = maxValue
}
