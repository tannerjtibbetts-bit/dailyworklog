// Documents (opened from the Links tab): photos of paper documents and uploaded files, shared by every project.
// Open, rename, share or delete them. The app doesn't fill anything in.
import 'package:flutter/material.dart';

import '../data/forms.dart';
import '../data/store.dart';
import '../services/form_files.dart';
import '../services/photos.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'form_layout.dart' show CropScreen;
import 'note_screen.dart' show SmallTopBar;
import 'projects_screen.dart' show AddPill;
import 'reports_screen.dart' show openDocument, shareDocument;

class DocumentsScreen extends StatefulWidget {
  final String projectId;

  const DocumentsScreen({super.key, required this.projectId});

  @override
  State<DocumentsScreen> createState() => _DocumentsScreenState();
}

class _DocumentsScreenState extends State<DocumentsScreen> {
  Store get store => Store.instance;
  final search = TextEditingController();

  String get query => search.text.trim().toLowerCase();

  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  void _refresh() {
    if (mounted) setState(() {});
  }

  /// The big +: a photo of a document (cropped) or a file.
  Future<void> _add() async {
    var choice = 0;
    await menu(context, null, [
      SheetItem(Icons.photo_camera_outlined, 'Take a photo of a document', () => choice = 1),
      SheetItem(Icons.upload_file, 'Upload a file', () => choice = 2),
    ]);
    if (!mounted || choice == 0) return;
    String? file;
    var suggested = '';
    if (choice == 1) {
      file = await Photos.take(context);
      if (file == null || !mounted) return;
      // Crop off anything around the page (the desk, or a screen's buttons).
      await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => CropScreen(file: file!)));
    } else {
      final picked = await FormFiles.pickFile(context);
      file = picked?.$1;
      suggested = picked?.$2 ?? '';
    }
    if (file == null || !mounted) return;
    final title = await prompt(context, 'Name this document', 'e.g. Lifting plan', suggested, 'Save');
    if (title == null) {
      FormFiles.deleteFiles([file]);
      return;
    }
    store.documents.add(
      Doc()
        ..title = title
        ..file = file
        ..kind = Doc.kindOf(file),
    );
    store.save();
    _refresh();
  }

  void _docMenu(Doc d) {
    menu(context, d.title, [
      SheetItem(Icons.open_in_new, 'Open', () => openDocument(context, d)),
      SheetItem(Icons.drive_file_rename_outline, 'Rename', () async {
        final t = await prompt(context, 'Rename document', 'Document name', d.title, 'Save');
        if (t == null) return;
        d.title = t;
        store.save();
        _refresh();
      }),
      SheetItem(Icons.share_outlined, 'Share', () => shareDocument(context, d)),
      SheetItem(Icons.delete_outline, 'Delete document', () {
        confirm(context, 'Delete this document?', '${d.title} will be removed from every project.', 'Delete', () {
          FormFiles.deleteFiles([d.file]);
          store.documents.remove(d);
          store.save();
          _refresh();
        });
      }, danger: true),
    ]);
  }

  Widget _docRow(Doc d) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Material(
          color: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(22),
            side: const BorderSide(color: C.cardStroke),
          ),
          child: InkWell(
            borderRadius: BorderRadius.circular(22),
            onTap: () => openDocument(context, d),
            onLongPress: () => _docMenu(d),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 80),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(18, 14, 14, 14),
                child: Row(children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(color: C.sky, borderRadius: BorderRadius.circular(16)),
                    child: Icon(
                      switch (d.kind) {
                        Doc.photo => Icons.image_outlined,
                        Doc.pdf => Icons.picture_as_pdf_outlined,
                        Doc.docx => Icons.description_outlined,
                        Doc.xlsx => Icons.grid_on,
                        _ => Icons.insert_drive_file_outlined,
                      },
                      color: C.deepBlue,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(d.title, style: const TextStyle(color: C.navy, fontSize: 20, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 3),
                      Text(
                        '${Doc.kindLabel(d.kind)} · ${FormFill.date(DateTime.fromMillisecondsSinceEpoch(d.addedAt))}',
                        style: const TextStyle(color: C.grey, fontSize: 15),
                      ),
                    ]),
                  ),
                  IconButton(
                    tooltip: 'More',
                    icon: const Icon(Icons.more_vert, color: C.grey, size: 28),
                    onPressed: () => _docMenu(d),
                  ),
                ]),
              ),
            ),
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final docs = [
      for (final d in List.of(store.documents)..sort((a, b) => b.addedAt.compareTo(a.addedAt)))
        if (query.isEmpty || d.title.toLowerCase().contains(query)) d,
    ];
    final empty = query.isNotEmpty
        ? 'Nothing matches “${search.text.trim()}”'
        : 'No documents yet.\nTap + to take a photo of a document or upload a file.\nDocuments show up in every project.';
    return Scaffold(
      backgroundColor: C.bg,
      body: SafeArea(
        child: Stack(children: [
          Column(children: [
            SmallTopBar(store.project(widget.projectId)?.name ?? '', 'Documents'),
            Container(
              height: 62,
              margin: const EdgeInsets.fromLTRB(18, 0, 18, 14),
              padding: const EdgeInsets.only(left: 20, right: 16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(30),
                border: Border.all(color: C.cardStroke),
              ),
              child: Row(children: [
                const Icon(Icons.search, color: C.navy, size: 26),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: search,
                    textInputAction: TextInputAction.search,
                    style: const TextStyle(fontSize: 18, color: C.text),
                    decoration: const InputDecoration(
                      hintText: 'Search documents',
                      hintStyle: TextStyle(color: C.grey),
                      border: InputBorder.none,
                    ),
                    onChanged: (_) => _refresh(),
                  ),
                ),
              ]),
            ),
            Expanded(
              child: ListView(padding: const EdgeInsets.fromLTRB(18, 0, 18, 110), children: [
                for (final d in docs) _docRow(d),
                if (docs.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(empty, textAlign: TextAlign.center, style: const TextStyle(color: C.grey, fontSize: 18)),
                  ),
              ]),
            ),
          ]),
          Positioned(left: 0, right: 0, bottom: 20, child: Center(child: AddPill('Add document', _add))),
        ]),
      ),
    );
  }
}
