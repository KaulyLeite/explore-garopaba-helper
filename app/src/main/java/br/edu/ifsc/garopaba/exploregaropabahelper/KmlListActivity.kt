package br.edu.ifsc.garopaba.exploregaropabahelper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class KmlListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: KmlAdapter
    private lateinit var kmlFiles: List<File>
    private lateinit var kmlFileDetails: MutableList<Pair<String, String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kml_list)

        listView = findViewById(R.id.kmlListView)

        val kmlDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "KMLRoutes")
        kmlFiles = kmlDir.listFiles()?.filter { it.extension == "kml" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (kmlFiles.isEmpty()) {
            Toast.makeText(this, "Nenhum trajeto encontrado", Toast.LENGTH_SHORT).show()
        } else {
            kmlFileDetails = kmlFiles.map { file ->
                val details = readKmlDescription(file)
                Pair(file.name, details)
            }.toMutableList()

            adapter = KmlAdapter(this, kmlFileDetails)
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, position, _ ->
                val selectedFile = kmlFiles[position]
                val intent = Intent(this, MapViewActivity::class.java)
                intent.putExtra("KML_FILE_NAME", selectedFile.name)
                intent.putExtra("KML_FILE_PATH", selectedFile.absolutePath)
                startActivity(intent)
            }

            listView.setOnItemLongClickListener { _, _, position, _ ->
                val selectedFile = kmlFiles[position]
                showDeleteConfirmationDialog(selectedFile, position)
                true
            }
        }
    }

    private inner class KmlAdapter(context: Context, kmlDetails: List<Pair<String, String>>) :
        ArrayAdapter<Pair<String, String>>(context, 0, kmlDetails) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val itemView = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_kml_list, parent, false)
            val kmlFileName = itemView.findViewById<TextView>(R.id.kmlFileName)
            val kmlDescription = itemView.findViewById<TextView>(R.id.kmlDescription)
            val (fileName, description) = getItem(position)!!

            kmlFileName.text = fileName
            kmlDescription.text = description

            return itemView
        }
    }

    private fun readKmlDescription(file: File): String {
        return try {
            val inputStream = file.inputStream()
            val builderFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = builderFactory.newDocumentBuilder()
            val doc = docBuilder.parse(inputStream)
            val descriptionNodes = doc.getElementsByTagName("description")

            if (descriptionNodes.length > 0) {
                descriptionNodes.item(0).textContent
            } else {
                "Sem informações adicionais"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Erro ao ler o arquivo KML"
        }
    }

    private fun showDeleteConfirmationDialog(file: File, position: Int) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Excluir Trajeto")
        builder.setMessage("Tem certeza de que deseja excluir o trajeto '${file.name}'?")
        builder.setPositiveButton("Excluir") { _, _ ->
            deleteFile(file, position)
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun deleteFile(file: File, position: Int) {
        if (file.exists()) {
            if (file.delete()) {
                Toast.makeText(this, "Trajeto excluído", Toast.LENGTH_SHORT).show()

                kmlFiles = kmlFiles.toMutableList().apply { removeAt(position) }
                kmlFileDetails.removeAt(position)
                adapter.notifyDataSetChanged()
            } else {
                Toast.makeText(this, "Erro ao excluir o trajeto", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
