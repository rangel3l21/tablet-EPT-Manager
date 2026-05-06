package deltazero.amarok.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import deltazero.amarok.R;
import deltazero.amarok.models.WishlistApp;

public class WishlistAdapter extends RecyclerView.Adapter<WishlistAdapter.VH> {

    private List<WishlistApp> apps;

    public WishlistAdapter(List<WishlistApp> apps) {
        this.apps = apps;
    }

    public void updateData(List<WishlistApp> apps) {
        this.apps = apps;
        notifyDataSetChanged();
    }

    public void updateAppStatus(WishlistApp app) {
        if (apps == null) return;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).packageName.equals(app.packageName)) {
                apps.set(i, app);
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wishlist, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        WishlistApp app = apps.get(position);
        holder.tvPackageName.setText(app.packageName);
        holder.tvStatus.setText(app.status);

        if (app.status.equals(WishlistApp.STATUS_ERROR)) {
            holder.card.setStrokeColor(Color.parseColor("#D32F2F"));
            holder.card.setStrokeWidth(4);
            holder.tvError.setVisibility(View.VISIBLE);
            holder.tvError.setText(app.errorMessage);
        } else if (app.status.equals(WishlistApp.STATUS_SUCCESS)) {
            holder.card.setStrokeColor(Color.parseColor("#388E3C"));
            holder.card.setStrokeWidth(4);
            holder.tvError.setVisibility(View.GONE);
        } else if (app.status.equals(WishlistApp.STATUS_INSTALLING) || app.status.equals(WishlistApp.STATUS_DOWNLOADING) || app.status.equals(WishlistApp.STATUS_FETCHING_URLS)) {
            holder.card.setStrokeColor(Color.parseColor("#1976D2"));
            holder.card.setStrokeWidth(4);
            holder.tvError.setVisibility(View.GONE);
        } else {
            // PENDING
            holder.card.setStrokeWidth(0);
            holder.tvError.setVisibility(View.GONE);
        }
    }

    @Override public int getItemCount() { return apps == null ? 0 : apps.size(); }

    class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvPackageName, tvStatus, tvError;
        VH(View v) {
            super(v);
            card = v.findViewById(R.id.card_wishlist);
            tvPackageName = v.findViewById(R.id.tv_package_name);
            tvStatus = v.findViewById(R.id.tv_status);
            tvError = v.findViewById(R.id.tv_error);
        }
    }
}
